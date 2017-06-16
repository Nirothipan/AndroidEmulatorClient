/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.carbon.android.emulator;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class starts the Emulator with the passed ID and log the output to emulator.log.
 */
public class TryItEmulator implements Runnable {
    private String deviceID;                    // name of the AVD to start
    private String emulatorLocation;            // location of the executable file emulator

    TryItEmulator(String id, String emulator) {
        deviceID = id;
        emulatorLocation = emulator;
    }

    public void run() {
        BufferedReader reader;
        ProcessBuilder processBuilder = new ProcessBuilder(emulatorLocation, "-avd", deviceID);
        try {
            String readLine;

            Process process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            try (FileWriter writer = new FileWriter("emulator.log")) {
                try {
                    while ((readLine = reader.readLine()) != null) {
                        writer.append(readLine);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
