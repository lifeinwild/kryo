/* Copyright (c) 2008-2022, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryo.util;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import static com.esotericsoftware.kryo.util.Util.*;
import static com.esotericsoftware.minlog.Log.*;

/** This is enhanced resolver from {@link DefaultClassResolver} for fast deserialization of collections.
 *
 * @author lifeinwild1@gmail.com
 * @apiNote In terms of functionality, {@link ArrayClassResolver} is completely equivalent to {@link DefaultClassResolver}. So the
 *          output binary is equivalent to that of {@link DefaultClassResolver}.
 * @implNote You can specify the mappings between class and ID by {@link Kryo#register(Class, int)}, But don't specify huge ID
 *           like 20000000 because this resolver uses array internally. This resolver internally reconstructs array of
 *           {@link IntToObjArray} when the mappings are added. Therefore, it is not suitable in terms of performance if the
 *           mappings are added frequently at peaktime of application. Use the {@link Pool}.
 * @see <a href="https://github.com/EsotericSoftware/kryo#pooling">Pool</a> */
public class ArrayClassResolver implements ClassResolver {
	protected final IdentityMap<Class, Registration> classToRegistration = new IdentityMap<>();
	private final IntToObjArray<Registration> idToRegistrationTmp = new IntToObjArray<>(Registration.class);
	protected Kryo kryo;
	protected IdentityObjectIntMap<Class> classToNameId;
	protected IntToObjArray<Class> nameIdToClass;
	protected ObjectMap<String, Class> nameToClass;
	protected int nextNameId;

	private int memoizedClassId = -1;
	private Registration memoizedClassIdValue;
	private Class memoizedClass;
	private Registration memoizedClassValue;

	public void setKryo (Kryo kryo) {
		this.kryo = kryo;
	}

	public Registration register (Registration registration) {
		memoizedClassId = -1;
		memoizedClass = null;
		if (registration == null) throw new IllegalArgumentException("registration cannot be null.");
		if (registration.getId() != DefaultClassResolver.NAME) {
			if (TRACE) {
				trace("kryo", "Register class ID " + registration.getId() + ": " + className(registration.getType()) + " ("
					+ registration.getSerializer().getClass().getName() + ")");
			}
			idToRegistrationTmp.put(registration.getId(), registration);
		} else if (TRACE) {
			trace("kryo", "Register class name: " + className(registration.getType()) + " ("
				+ registration.getSerializer().getClass().getName() + ")");
		}
		classToRegistration.put(registration.getType(), registration);
		Class wrapperClass = getWrapperClass(registration.getType());
		if (wrapperClass != registration.getType()) classToRegistration.put(wrapperClass, registration);
		return registration;
	}

	public Registration registerImplicit (Class type) {
		return register(new Registration(type, kryo.getDefaultSerializer(type), DefaultClassResolver.NAME));
	}

	public Registration getRegistration (Class type) {
		if (type == memoizedClass) return memoizedClassValue;
		Registration registration = classToRegistration.get(type);
		if (registration != null) {
			memoizedClass = type;
			memoizedClassValue = registration;
		}
		return registration;
	}

	public Registration writeClass (Output output, Class type) {
		if (type == null) {
			if (TRACE || (DEBUG && kryo.getDepth() == 1)) log("Write", null, output.position());
			output.writeByte(Kryo.NULL);
			return null;
		}
		Registration registration = kryo.getRegistration(type);
		if (registration.getId() == DefaultClassResolver.NAME)
			writeName(output, type, registration);
		else {
			if (TRACE) trace("kryo", "Write class " + registration.getId() + ": " + className(type) + pos(output.position()));
			output.writeVarInt(registration.getId() + 2, true);
		}
		return registration;
	}

	protected void writeName (Output output, Class type, Registration registration) {
		output.writeByte(1); // NAME + 2
		if (classToNameId != null) {
			int nameId = classToNameId.get(type, -1);
			if (nameId != -1) {
				if (TRACE) trace("kryo", "Write class name reference " + nameId + ": " + className(type) + pos(output.position()));
				output.writeVarInt(nameId, true);
				return;
			}
		}
		// Only write the class name the first time encountered in object graph.
		if (TRACE) trace("kryo", "Write class name: " + className(type) + pos(output.position()));
		int nameId = nextNameId++;
		if (classToNameId == null) classToNameId = new IdentityObjectIntMap<>();
		classToNameId.put(type, nameId);
		output.writeVarInt(nameId, true);
		if (registration.isTypeNameAscii())
			output.writeAscii(type.getName());
		else
			output.writeString(type.getName());
	}

	protected Registration readName (Input input) {
		int nameId = input.readVarInt(true);
		if (nameIdToClass == null) nameIdToClass = new IntToObjArray<>(Class.class, nameId + 20);
		Class type = nameIdToClass.get(nameId);
		if (type == null) {
			// Only read the class name the first time encountered in object graph.
			String className = input.readString();
			type = getTypeByName(className);
			if (type == null) {
				try {
					type = Class.forName(className, false, kryo.getClassLoader());
				} catch (ClassNotFoundException ex) {
					// Fallback to Kryo's class loader.
					try {
						type = Class.forName(className, false, Kryo.class.getClassLoader());
					} catch (ClassNotFoundException ex2) {
						throw new KryoException("Unable to find class: " + className, ex);
					}
				}
				if (nameToClass == null) nameToClass = new ObjectMap<>();
				nameToClass.put(className, type);
			}
			nameIdToClass.put(nameId, type);
			if (TRACE) trace("kryo", "Read class name: " + className + pos(input.position()));
		} else {
			if (TRACE) trace("kryo", "Read class name reference " + nameId + ": " + className(type) + pos(input.position()));
		}
		return kryo.getRegistration(type);
	}

	protected Class getTypeByName (final String className) {
		return nameToClass != null ? nameToClass.get(className) : null;
	}

	public void reset () {
		if (!kryo.isRegistrationRequired()) {
			if (classToNameId != null) classToNameId.clear(2048);
			if (nameIdToClass != null) nameIdToClass.clear();
			nextNameId = 0;
		}
	}

	@Override
	public final Registration getRegistration (int classID) {
		return idToRegistrationTmp.get(classID);
	}

	@Override
	public Registration readClass (Input input) {
		int classID = input.readVarInt(true);
		switch (classID) {
		case Kryo.NULL:
			if (TRACE || (DEBUG && kryo.getDepth() == 1)) log("Read", null, input.position());
			return null;
		case DefaultClassResolver.NAME + 2: // Offset for NAME and NULL.
			return readName(input);
		}

		if (classID == memoizedClassId) {
			if (TRACE) trace("kryo",
				"Read class " + (classID - 2) + ": " + className(memoizedClassIdValue.getType()) + pos(input.position()));
			return memoizedClassIdValue;
		}

		int index = classID - 2;
		Registration registration = getRegistration(index);
		if (registration == null) throw new KryoException("Encountered unregistered class ID: " + (classID - 2));
		if (TRACE) trace("kryo", "Read class " + (classID - 2) + ": " + className(registration.getType()) + pos(input.position()));

		memoizedClassId = classID;
		memoizedClassIdValue = registration;

		return registration;
	}

	public Registration unregister (int classID) {
		Registration registration = idToRegistrationTmp.remove(classID);
		if (registration != null) {
			classToRegistration.remove(registration.getType());
			memoizedClassId = -1;
			memoizedClass = null;
			Class wrapperClass = getWrapperClass(registration.getType());
			if (wrapperClass != registration.getType()) classToRegistration.remove(wrapperClass);
		}
		return registration;
	}
}
