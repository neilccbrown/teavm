/*
 *  Copyright 2025 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.nio;

import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.DataView;
import org.teavm.jso.typedarrays.Int8Array;

class TShortBufferOverDataView extends TShortBufferImpl implements TArrayBufferViewProvider {
    private boolean readOnly;
    private DataView data;
    private boolean littleEndian;

    TShortBufferOverDataView(int position, int limit, boolean readOnly, DataView data, boolean littleEndian) {
        super(position, limit);
        this.readOnly = readOnly;
        this.data = data;
        this.littleEndian = littleEndian;
    }

    @Override
    TShortBuffer duplicate(int start, int capacity, int position, int limit, boolean readOnly) {
        var slice = new DataView(data.getBuffer(), data.getByteOffset() + start * 2, capacity * 2);
        return new TShortBufferOverDataView(position, limit, readOnly, slice, littleEndian);
    }

    @Override
    boolean readOnly() {
        return readOnly;
    }

    @Override
    short getElement(int index) {
        return data.getInt16(index * 2, littleEndian);
    }

    @Override
    void putElement(int index, short value) {
        data.setInt16(index * 2, value, littleEndian);
    }

    @Override
    void getImpl(int index, short[] dst, int offset, int length) {
        index *= 2;
        while (length-- > 0) {
            dst[offset++] = data.getInt16(index, littleEndian);
            index += 2;
        }
    }

    @Override
    void putImpl(int index, TShortBuffer src, int offset, int length) {
        if (src instanceof TShortBufferOverDataView) {
            length *= 2;
            var srcImpl = (TShortBufferOverDataView) src;
            var srcArray = new Int8Array(srcImpl.data.getBuffer(), srcImpl.data.getByteOffset() + offset * 2, length);
            var destArray = new Int8Array(data.getBuffer(), data.getByteOffset() + index * 2, length);
            destArray.set(srcArray, 0);
        } else {
            index *= 2;
            while (length-- > 0) {
                data.setInt16(index, src.get(offset++), littleEndian);
                index += 2;
            }
        }
    }

    @Override
    void putImpl(int index, short[] src, int offset, int length) {
        index *= 2;
        while (length-- > 0) {
            data.setInt16(index, src[offset++], littleEndian);
            index += 2;
        }
    }

    @Override
    boolean isArrayPresent() {
        return false;
    }

    @Override
    short[] getArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    int getArrayOffset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TByteOrder order() {
        return littleEndian ? TByteOrder.LITTLE_ENDIAN : TByteOrder.BIG_ENDIAN;
    }

    @Override
    int capacityImpl() {
        return data.getByteLength() / 2;
    }

    @Override
    public ArrayBufferView getArrayBufferView() {
        return data;
    }

    @Override
    public int elementSize() {
        return 2;
    }
}
