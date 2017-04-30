/* Copyright (c) 2001-2015, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package third.hsqldb.rowio;

import third.hsqldb.Row;
import third.hsqldb.lib.HashMappedList;
import third.hsqldb.lib.HsqlByteArrayOutputStream;
import third.hsqldb.types.Type;

/**
 * Public interface for writing the data for a database row.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.3.3
 * @since 1.7.0
 */
public interface RowOutputInterface extends Cloneable {

    void setStorageSize(int size);

    void writeEnd();

    void writeSize(int size);

    void writeType(int type);

    void writeString(String value);

    void writeByte(int i);

    void writeChar(int i);

    void writeShort(int i);

    void writeInt(int i);

    void writeLong(long i);

    void writeData(Object object, Type type);

    void writeData(Row row, Type[] types);

    void writeData(int l, Type[] types, Object[] data, HashMappedList cols,
                   int[] primarykeys);

    int getSize(Row row);

    int getStorageSize(int size);

    // returns the underlying HsqlByteArrayOutputStream
    HsqlByteArrayOutputStream getOutputStream();

    byte[] getBuffer();

    // resets the byte[] buffer, ready for processing new row
    void reset();

    // performs reset() and ensures byte[] buffer is at least newSize
    void reset(int newSize);

    // sets the byte[] buffer
    void reset(byte[] mainBuffer);

    // returns the current size
    int size();

    RowOutputInterface duplicate();
}
