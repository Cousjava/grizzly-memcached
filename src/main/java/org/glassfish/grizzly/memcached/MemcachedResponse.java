/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.grizzly.memcached;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.MemoryManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Memcached response
 * <p>
 * Key and value will be decoded by {@link #setDecodedKey} and {@link #setDecodedValue}.
 * {@link #setResult} sets the last result based on other fields of this response in according to memcached's command.
 *
 * @author Bongjae Chang
 */
public class MemcachedResponse implements Cacheable {

    private static final Logger logger = Grizzly.logger(MemcachedResponse.class);

    private static final Long INVALID_LONG = (long) -1;
    private static final ThreadCache.CachedTypeIndex<MemcachedResponse> CACHE_IDX =
            ThreadCache.obtainIndex(MemcachedResponse.class, 16);

    // header
    private CommandOpcodes op;
    private short keyLength;
    private byte extraLength;
    private byte dataType;
    private ResponseStatus status;
    private int totalBodyLength;
    private int opaque;
    private long cas;

    // extras and body
    private int flags;

    private Object decodedKey;
    private Object decodedValue;
    private Object result;

    private MemcachedResponse() {
    }

    public static MemcachedResponse create() {
        final MemcachedResponse response = ThreadCache.takeFromCache(CACHE_IDX);
        if (response != null) {
            return response;
        }
        return new MemcachedResponse();
    }

    public CommandOpcodes getOp() {
        return op;
    }

    public short getKeyLength() {
        return keyLength;
    }

    public byte getExtraLength() {
        return extraLength;
    }

    public byte getDataType() {
        return dataType;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public int getTotalBodyLength() {
        return totalBodyLength;
    }

    public int getOpaque() {
        return opaque;
    }

    public long getCas() {
        return cas;
    }

    public int getFlags() {
        return flags;
    }

    public Object getDecodedKey() {
        return decodedKey;
    }

    public Object getDecodedValue() {
        return decodedValue;
    }

    public Object getResult() {
        return result;
    }

    public void setOp(final CommandOpcodes op) {
        this.op = op;
    }

    public void setKeyLength(final short keyLength) {
        this.keyLength = keyLength;
    }

    public void setExtraLength(final byte extraLength) {
        this.extraLength = extraLength;
    }

    public void setDataType(final byte dataType) {
        this.dataType = dataType;
    }

    public void setStatus(final ResponseStatus status) {
        this.status = status;
    }

    public void setTotalBodyLength(final int totalBodyLength) {
        this.totalBodyLength = totalBodyLength;
    }

    public void setOpaque(final int opaque) {
        this.opaque = opaque;
    }

    public void setCas(final long cas) {
        this.cas = cas;
    }

    public void setFlags(final int flags) {
        this.flags = flags;
    }

    public void setDecodedKey(final Buffer buffer, final int position, final int limit, final MemoryManager memoryManager) {
        if (buffer == null || position > limit) {
            return;
        }
        final Object result;
        switch (op) {
            case Stat:
                if (!isError()) {
                    result = BufferWrapper.unwrap(buffer, position, limit, BufferWrapper.BufferType.STRING, memoryManager);
                } else {
                    result = null;
                }
                break;
            default:
                result = null;
                break;
        }
        decodedKey = result;
    }

    public void setDecodedKey(final Object decodedKey) {
        this.decodedKey = decodedKey;
    }

    public void setDecodedValue(final Buffer buffer, final int position, final int limit, final MemoryManager memoryManager) {
        if (buffer == null || position > limit) {
            return;
        }
        final Object result;
        switch (op) {
            // user value type
            case Get:
            case GetQ:
            case GAT:
            case GATQ:
            case GetK:
            case GetKQ:
            case Gets:
            case GetsQ:
                if (!isError()) {
                    result = BufferWrapper.unwrap(buffer, position, limit, BufferWrapper.BufferType.getBufferType(this.flags), memoryManager);
                } else {
                    result = null;
                }
                break;
            case Increment:
            case Decrement:
                if (!isError()) {
                    result = BufferWrapper.unwrap(buffer, position, limit, BufferWrapper.BufferType.LONG, memoryManager);
                } else {
                    result = INVALID_LONG;
                }
                break;

            case Version:
            case Stat:
                if (!isError()) {
                    result = BufferWrapper.unwrap(buffer, position, limit, BufferWrapper.BufferType.STRING, memoryManager);
                } else {
                    result = null;
                }
                break;
            default:
                result = null;
                break;
        }
        decodedValue = result;
    }

    public void setDecodedValue(final Object decodedValue) {
        this.decodedValue = decodedValue;
    }

    @SuppressWarnings("unchecked")
    public <K> void setResult(final K originKey, final MemcachedClientFilter.ParsingStatus parsingStatus) {
        if (isError() && parsingStatus == MemcachedClientFilter.ParsingStatus.DONE) {
            if (status == ResponseStatus.Key_Not_Found) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "error status code={0}, status msg={1}, op={2}, key={3}", new Object[]{status, status.message(), op, originKey});
                }
            } else {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "error status code={0}, status msg={1}, op={2}, key={3}", new Object[]{status, status.message(), op, originKey});
                }
            }
        }

        final Object result;
        switch (op) {
            // user value type
            case Get:
            case GetQ:
            case GAT:
            case GATQ:
                if (!isError()) {
                    result = decodedValue;
                } else {
                    result = null;
                }
                break;

            // ValueWithKey type
            case GetK:
            case GetKQ:
                if (!isError() && decodedValue != null) {
                    result = new ValueWithKey(originKey, decodedValue);
                } else {
                    result = null;
                }
                break;

            // ValueWithCas type
            case Gets:
            case GetsQ:
                if (!isError() && decodedValue != null) {
                    result = new ValueWithCas(decodedValue, this.cas);
                } else {
                    result = null;
                }
                break;

            // boolean and void type. there are no responses except for error
            case Set:
            case Add:
            case Replace:
            case Delete:
            case Quit:
            case Flush:
            case Append:
            case Prepend:
            case Verbosity:
            case Touch:
            case Noop:
            case SetQ:
            case AddQ:
            case ReplaceQ:
            case DeleteQ:
            case IncrementQ:
            case DecrementQ:
            case QuitQ:
            case FlushQ:
            case AppendQ:
            case PrependQ:
                if (!isError() || parsingStatus == MemcachedClientFilter.ParsingStatus.NO_REPLY) {
                    result = Boolean.TRUE;
                } else {
                    result = Boolean.FALSE;
                }
                break;

            // long type
            case Increment:
            case Decrement:
                if (!isError() && decodedValue instanceof Long) {
                    result = decodedValue;
                } else {
                    result = INVALID_LONG;
                }
                break;

            // string type
            case Version:
                if (!isError() && decodedValue instanceof String) {
                    result = decodedValue;
                } else {
                    result = null;
                }
                break;

            case Stat:
                if (!isError() && decodedKey instanceof String && decodedValue instanceof String) {
                    result = new ValueWithKey<String, String>((String) decodedKey, (String) decodedValue);
                } else {
                    result = null;
                }
                break;

            // currently not supported
            case SASL_List:
            case SASL_Auth:
            case SASL_Step:
            case RGet:
            case RSet:
            case RSetQ:
            case RAppend:
            case RAppendQ:
            case RPrepend:
            case RPrependQ:
            case RDelete:
            case RDeleteQ:
            case RIncr:
            case RIncrQ:
            case RDecr:
            case RDecrQ:
            case Set_VBucket:
            case Get_VBucket:
            case Del_VBucket:
            case TAP_Connect:
            case TAP_Mutation:
            case TAP_Delete:
            case TAP_Flush:
            case TAP_Opaque:
            case TAP_VBucket_Set:
            case TAP_Checkpoint_Start:
            case TAP_Checkpoint_End:
            default:
                result = null;
                break;
        }
        this.result = result;
    }

    public boolean isError() {
        switch (op) {
            case Delete:
            case DeleteQ:
                return status != null && status != ResponseStatus.No_Error && status != ResponseStatus.Key_Not_Found;
            default:
                return status != null && status != ResponseStatus.No_Error;
        }
    }

    public boolean complete() {
        switch (op) {
            // one request - many response
            case Stat:
                return decodedKey == null || decodedValue == null;
            //return key == null || value == null;
            default:
                return true;
        }
    }

    public void clear() {
        op = null;
        keyLength = 0;
        extraLength = 0;
        dataType = 0;
        status = null;
        totalBodyLength = 0;
        opaque = 0;
        cas = 0;
        flags = 0;
        decodedKey = null;
        decodedValue = null;
        result = null;
    }

    @Override
    public void recycle() {
        clear();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public String toString() {
        return "MemcachedResponse{" +
                "op=" + op +
                ", keyLength=" + keyLength +
                ", extraLength=" + extraLength +
                ", dataType=" + dataType +
                ", status=" + status +
                ", totalBodyLength=" + totalBodyLength +
                ", opaque=" + opaque +
                ", cas=" + cas +
                ", flags=" + flags +
                ", decodedKey=" + decodedKey +
                ", decodedValue=" + decodedValue +
                ", result=" + result +
                '}';
    }
}
