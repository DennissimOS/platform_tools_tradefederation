/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.testtype;

/**
 * A class representing an ABI.
 */
public class Abi implements IAbi {

    private final String mName;
    private final String mBitness;

    public Abi(String name, String bitness) {
        mName = name;
        mBitness = bitness;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBitness() {
        return mBitness;
    }

    @Override
    public String toString() {
        return "{" + mName + ", bitness=" + mBitness + "}";
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        Abi other = (Abi) obj;
        if (!mName.equals(other.mName)) {
            return false;
        }
        if (!mBitness.equals(other.mBitness)) {
            return false;
        }
        return true;
    }
}