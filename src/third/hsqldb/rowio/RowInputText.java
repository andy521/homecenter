/* Copyright (c) 2001-2016, The HSQL Development Group
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


import java.math.BigDecimal;

import third.hsqldb.Scanner;
import third.hsqldb.Tokens;
import third.hsqldb.error.Error;
import third.hsqldb.error.ErrorCode;
import third.hsqldb.map.ValuePool;
import third.hsqldb.persist.TextFileSettings;
import third.hsqldb.types.BinaryData;
import third.hsqldb.types.BlobData;
import third.hsqldb.types.BlobDataID;
import third.hsqldb.types.ClobData;
import third.hsqldb.types.ClobDataID;
import third.hsqldb.types.IntervalMonthData;
import third.hsqldb.types.IntervalSecondData;
import third.hsqldb.types.IntervalType;
import third.hsqldb.types.JavaObjectData;
import third.hsqldb.types.TimeData;
import third.hsqldb.types.TimestampData;
import third.hsqldb.types.Type;
import third.hsqldb.types.Types;


/**
 * Class for reading the data for a database row in text table format.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @version 2.3.4
 * @since 1.7.0
 */
public class RowInputText extends RowInputBase implements RowInputInterface {

    // text table specific
    protected TextFileSettings textFileSettings;
    private String             fieldSep;
    private String             varSep;
    private String             longvarSep;
    private int                fieldSepLen;
    private int                varSepLen;
    private int                longvarSepLen;
    private boolean            fieldSepEnd;
    private boolean            varSepEnd;
    private boolean            longvarSepEnd;
    private int                textLen;
    protected String           text;
    protected long             line;
    protected int              field;
    protected int              next = 0;
    protected Scanner          scanner;

    //
    private int maxPooledStringLength = ValuePool.getMaxStringLength();

    /**
     * fredt@users - comment - in future may use a custom subclasse of
     * InputStream to read the data.
     */
    public RowInputText(TextFileSettings textFileSettings) {

        super(new byte[0]);

        scanner               = new Scanner();
        this.textFileSettings = textFileSettings;
        this.fieldSep         = textFileSettings.fs;
        this.varSep           = textFileSettings.vs;
        this.longvarSep       = textFileSettings.lvs;

        //-- Newline indicates that field should match to end of line.
        if (fieldSep.endsWith("\n")) {
            fieldSepEnd = true;
            fieldSep    = fieldSep.substring(0, fieldSep.length() - 1);
        }

        if (varSep.endsWith("\n")) {
            varSepEnd = true;
            varSep    = varSep.substring(0, varSep.length() - 1);
        }

        if (longvarSep.endsWith("\n")) {
            longvarSepEnd = true;
            longvarSep    = longvarSep.substring(0, longvarSep.length() - 1);
        }

        fieldSepLen   = fieldSep.length();
        varSepLen     = varSep.length();
        longvarSepLen = longvarSep.length();
    }

    public void setSource(String text, long pos, int byteSize) {

        size      = byteSize;
        this.text = text;
        textLen   = text.length();
        filePos   = pos;
        next      = 0;

        line++;

        field = 0;
    }

    protected String getField(String sep, int sepLen, boolean isEnd) {

        String s = null;

        try {
            int start = next;

            field++;

            if (isEnd) {
                if ((next >= textLen) && (sepLen > 0)) {
                    throw Error.error(ErrorCode.TEXT_SOURCE_NO_END_SEPARATOR);
                } else if (text.endsWith(sep)) {
                    next = textLen - sepLen;
                } else {
                    throw Error.error(ErrorCode.TEXT_SOURCE_NO_END_SEPARATOR);
                }
            } else {
                next = text.indexOf(sep, start);

                if (next == -1) {
                    next = textLen;
                }
            }

            if (start > next) {
                start = next;
            }

            s    = text.substring(start, next);
            next += sepLen;

            int trimLength = s.trim().length();

            if (trimLength == 0) {
                s = null;
            } else if (trimLength < s.length()) {
                trimLength = s.length() - 1;

                while (s.charAt(trimLength) < ' ') {
                    trimLength--;
                }

                s = s.substring(0, trimLength + 1);
            }
        } catch (Exception e) {
            String message = e.toString();

            throw Error.error(e, ErrorCode.M_TEXT_SOURCE_FIELD_ERROR, message);
        }

        return s;
    }

    public String readString() {
        return getField(fieldSep, fieldSepLen, fieldSepEnd);
    }

    private String readVarString() {
        return getField(varSep, varSepLen, varSepEnd);
    }

    /**
     * Obsoleted in 1.9.0
     */
    private String readLongVarString() {
        return getField(longvarSep, longvarSepLen, longvarSepEnd);
    }

    public char readChar() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public byte readByte() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public short readShort() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public int readInt() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public long readLong() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public int readType() {
        return 0;
    }

    protected boolean readNull() {

        // Return null on each column read instead.
        return false;
    }

    /**
     * This does not check the length of the character string.
     * The text file may contain strings that are longer than allowed by
     * the declared type.
     */
    protected String readChar(Type type) {

        String s = null;

        switch (type.typeCode) {

            case Types.SQL_CHAR :
                s = readString();
                break;

            case Types.SQL_VARCHAR :
                s = readVarString();
                break;

            default :
                s = readLongVarString();
                break;
        }

        if (s == null) {
            return null;
        }

        if (s.length() > this.maxPooledStringLength) {
            return s;
        } else {
            return ValuePool.getString(s);
        }
    }

    protected Integer readSmallint() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return ValuePool.getInt(Integer.parseInt(s));
    }

    protected Integer readInteger() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return ValuePool.getInt(Integer.parseInt(s));
    }

    protected Long readBigint() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return ValuePool.getLong(Long.parseLong(s));
    }

    protected Double readReal() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return Double.valueOf(s);
    }

    protected BigDecimal readDecimal(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return new BigDecimal(s);
    }

    protected TimeData readTime(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return scanner.newTime(s);
    }

    protected TimestampData readDate(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return scanner.newDate(s);
    }

    protected TimestampData readTimestamp(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return scanner.newTimestamp(s);
    }

    protected IntervalMonthData readYearMonthInterval(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return (IntervalMonthData) scanner.newInterval(s, (IntervalType) type);
    }

    protected IntervalSecondData readDaySecondInterval(Type type) {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return (IntervalSecondData) scanner.newInterval(s,
                (IntervalType) type);
    }

    protected Boolean readBoole() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        return s.equalsIgnoreCase(Tokens.T_TRUE) ? Boolean.TRUE
                                                 : Boolean.FALSE;
    }

    protected Object readOther() {

        String s = readString();

        if (s == null) {
            return null;
        }

        BinaryData data = scanner.convertToBinary(s);

        if (data.length(null) == 0) {
            return null;
        }

        return new JavaObjectData(data.getBytes());
    }

    protected BinaryData readBit() {

        String s = readString();

        if (s == null) {
            return null;
        }

        BinaryData data = scanner.convertToBit(s);

        return data;
    }

    protected BinaryData readBinary() {

        String s = readString();

        if (s == null) {
            return null;
        }

        BinaryData data = scanner.convertToBinary(s);

        return data;
    }

    protected ClobData readClob() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        long id = Long.parseLong(s);

        return new ClobDataID(id);
    }

    protected BlobData readBlob() {

        String s = readString();

        if (s == null) {
            return null;
        }

        s = s.trim();

        if (s.length() == 0) {
            return null;
        }

        long id = Long.parseLong(s);

        return new BlobDataID(id);
    }

    protected Object[] readArray(Type type) {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputText");
    }

    public long getLineNumber() {
        return line;
    }

    public void skippedLine() {
        line++;
    }

    public void reset() {

        text    = "";
        textLen = 0;
        filePos = 0;
        next    = 0;
        field   = 0;
        line    = 0;
    }
}
