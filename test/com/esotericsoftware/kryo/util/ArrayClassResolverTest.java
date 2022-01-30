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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This class has only few test, but I tested {@link ArrayClassResolver} fully by below method:
 * I temporarily modified the kryo instance of {@link KryoTestCase#setUp()}, then all test cases passed.
 *
 * <pre>
 * @BeforeEach
 * public void setUp () throws Exception {
 *	 if (debug && WARN) warn("*** DEBUG TEST ***");
 *
 * 	 kryo = new Kryo(new ArrayClassResolver(), null);
 * }
 *
 * Tests passed: 267 of 267 tests
 * </pre>
 *
 * @author lifeinwild1@gmail.com
 */
public class ArrayClassResolverTest extends KryoTestCase {
    @BeforeEach
    public void setUp () throws Exception {
        super.setUp();

        ArrayClassResolver resolver = new ArrayClassResolver();
        kryo = new Kryo(resolver, null);
        kryo.register(ArrayList.class);
    }

    @Test
    void testBasic () {
        ArrayList test = new ArrayList();
        test.add("one");
        test.add("two");
        test.add("three");

        ArrayList copy = kryo.copy(test);
        assertNotSame(test, copy);
        assertEquals(test, copy);
    }


}