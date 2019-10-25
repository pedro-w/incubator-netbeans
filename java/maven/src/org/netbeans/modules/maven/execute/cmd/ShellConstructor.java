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

package org.netbeans.modules.maven.execute.cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.modules.maven.options.MavenSettings;
import org.openide.util.Utilities;

/**
 * TODO candidate to merge back into MavenCommandLineExecutor
 * @author mkleint
 */
public class ShellConstructor implements Constructor {
    private final @NonNull File mavenHome;

    public ShellConstructor(@NonNull File mavenHome) {
        this.mavenHome = mavenHome;
    }

    @Override
    public List<String> construct() {
        List<String> toRet = new ArrayList<>();
        String ex = "mvn"; //NOI18N
        if (Utilities.isWindows()) {
            toRet.add("cmd"); //NOI18N
            toRet.add("/c"); //NOI18N
            String version = MavenSettings.getCommandLineMavenVersion(mavenHome);
            if (null == version) {
                ex = "mvn.bat"; // NOI18N
            } else {
                String[] v = version.split("\\."); // NOI18N
                int major = Integer.parseInt(v[0]);
                int minor = Integer.parseInt(v[1]);
                // starting with 3.3.0 maven stop using .bat file
                if ((major < 3) || (major == 3 && minor < 3)) {
                    ex = "mvn.bat"; //NOI18N
                } else {
                    ex = "mvn.cmd"; //NOI18N
                }
            }
        }
        File bin = new File(mavenHome, "bin" + File.separator + ex);//NOI18N
        toRet.add(bin.getAbsolutePath());
        return toRet;
    }
}
