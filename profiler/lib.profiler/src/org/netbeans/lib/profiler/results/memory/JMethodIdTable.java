/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.lib.profiler.results.memory;

import org.netbeans.lib.profiler.ProfilerClient;
import org.netbeans.lib.profiler.client.ClientUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * This class maps jmethodIds to (class name, method name, method signature) 
 *
 * @author Misha Dmitriev
 */
public class JMethodIdTable {
    //~ Inner Classes ------------------------------------------------------------------------------------------------------------
    /** 
     * Holds the details of a Method ID.
     */
    public static class JMethodIdTableEntry {
        //~ Instance fields ------------------------------------------------------------------------------------------------------

        public String className;
        public String methodName;
        public String methodSig;
        public transient boolean isNative;
        public final long methodId;

        //~ Constructors ---------------------------------------------------------------------------------------------------------

        JMethodIdTableEntry(long methodId) {
            this.methodId = methodId;
        }
    }

    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    public final static String NATIVE_SUFFIX = "[native]";   // NOI18N
    private static JMethodIdTable defaultTable;

    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private final Map<Long, JMethodIdTableEntry> entries = new HashMap<>(97);
    private boolean staticTable = false;
    private boolean incompleteEntries = false;

    //~ Constructors -------------------------------------------------------------------------------------------------------------
    /**
     * Create a new, empty table.
     */
    public JMethodIdTable() {
    }

    /** 
     * Construct this table as a copy of another.
     * This table will not share any data with the other table.
     * @param otherTable another table.
     */
    public JMethodIdTable(JMethodIdTable otherTable) {
        staticTable = true;
        for (JMethodIdTableEntry entry : otherTable.entries.values()) {
            addEntry(entry.methodId, entry.className, entry.methodName, entry.methodSig, entry.isNative);
        }
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------
    /**
     * Return the default instance of the table.
     * Repeated calls will return the same object.
     * @return the table
     */
    synchronized public static JMethodIdTable getDefault() {
        if (defaultTable == null) {
            defaultTable = new JMethodIdTable();
        }

        return defaultTable;
    }
    /**
     * Reset the default table.
     * The next call to {@link #getDefault()} will return a new object
     */
    synchronized public static void reset() {
        defaultTable = null;
    }
    /** 
     * Return a representation for debugging.
     * @return the string
     */
    synchronized public String debug() {
            return "Entries.size() = " + entries.size() + ", incompleteEntries = " + incompleteEntries; // NOI18N
    }
    /**
     * Read this table back from a data stream.
     * See notes on format in {@link #writeToStream(java.io.DataOutputStream)}
     * @param in the stream to read from
     * @throws IOException 
     */
    synchronized public void readFromStream(DataInputStream in) throws IOException {
        entries.clear();
        int count = in.readInt();

        for (int i = 0; i < count; i++) {
            long methodId = in.readLong();
            String className = in.readUTF();
            String methodName = in.readUTF();
            String methodSig = in.readUTF();
            boolean isNative;
            
            if (methodName.endsWith(NATIVE_SUFFIX)) {
                methodName = methodName.substring(0, methodName.length() - NATIVE_SUFFIX.length());
                isNative = true;
            } else {
                isNative = false;
            }
            addEntry(methodId, className, methodName, methodSig, isNative);
        }
    }
    /**
     * Write this table to a data stream.
     * The format is
     * <ul>
     * <li> count (int)
     * </ul>
     * Then the entries (repeated 'count' times):
     * <ul>
     * <li> ID (long)
     * <li> Class Name (UTF-8 string)
     * <li> Method Name (UTF-8 string)
     * <li> Method Signature (UTF-8 string)
     * </ul>
     * @param out the stream to write to
     * @throws IOException 
     */
    synchronized public void writeToStream(DataOutputStream out) throws IOException {
        int count = entries.size();

        out.writeInt(count);

        for (JMethodIdTableEntry entry : entries.values()) {
            out.writeLong(entry.methodId);
            out.writeUTF(entry.className);
            out.writeUTF(entry.isNative ? entry.methodName.concat(NATIVE_SUFFIX) : entry.methodName);
            out.writeUTF(entry.methodSig);
        }
    }
    /**
     * Get the entry for a given Method ID.
     * Returns null if the ID has never been added.
     * @param methodId the method ID
     * @return the corresponding entry
     */
    synchronized public JMethodIdTableEntry getEntry(long methodId) {
        return entries.get(methodId);
    }

    /**
     * Ask the Profiler Client to fetch the details of any incomplete entries.
     * @param profilerClient the client connection
     * @throws org.netbeans.lib.profiler.client.ClientUtils.TargetAppOrVMTerminated 
     */
    synchronized public void getNamesForMethodIds(ProfilerClient profilerClient)
                                    throws ClientUtils.TargetAppOrVMTerminated {
        if (staticTable) {
            throw new IllegalStateException("Attempt to update snapshot JMethodIdTable"); // NOI18N
        }

        if (!incompleteEntries) {
            return;
        }
        ArrayList<JMethodIdTableEntry> incomplete = new ArrayList<>();
        for (JMethodIdTableEntry entry : entries.values()) {
            if (entry.className == null) {
                incomplete.add(entry);
            }
        }
        long[] missingNameMethodIds = new long[incomplete.size()];
        
        for (int i=0; i< missingNameMethodIds.length; ++i) {
                missingNameMethodIds[i] = incomplete.get(i).methodId;
        }

        String[][] methodClassNameAndSig = profilerClient.getMethodNamesForJMethodIds(missingNameMethodIds);

        for (int i = 0; i < missingNameMethodIds.length; i++) {
            JMethodIdTableEntry entry = incomplete.get(i);
            entry.className = methodClassNameAndSig[0][i];
            entry.methodName = methodClassNameAndSig[1][i];
            entry.methodSig = methodClassNameAndSig[2][i];
            entry.isNative = getBoolean(methodClassNameAndSig[3][i]);
        }

        incompleteEntries = false;
    }

    private void addEntry(long methodId, String className, String methodName, String methodSig, boolean isNative) {
        JMethodIdTableEntry entry = entries.putIfAbsent(methodId, new JMethodIdTableEntry(methodId));
        entry.className = className;
        entry.methodName = methodName;
        entry.methodSig = methodSig;
        entry.isNative = isNative;
    }
    /**
     * Add an incomplete entry for a Method ID.
     * This can be resolved later with 
     * {@link #getNamesForMethodIds(ProfilerClient)}
     * @param methodId the method id.
     */
    synchronized public void checkMethodId(long methodId) {
        if (!entries.containsKey(methodId)) {
            entries.put(methodId, new JMethodIdTableEntry(methodId));
            incompleteEntries = true;
        }
    }

    private boolean getBoolean(String boolStr) {
        return "1".equals(boolStr);       // NOI18N
    }
}
