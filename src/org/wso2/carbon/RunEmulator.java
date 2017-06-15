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
package org.wso2.carbon;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class starts the Emulator with the passed ID and log the output to emulator.log
 */
public class RunEmulator implements Runnable {
    private Thread thread;
    private String device_id;                    // name of the emulator to start
    private String emulator_location;            // location of the executable file emulator

    RunEmulator(String id, String emulator) {
        device_id = id;
        emulator_location = emulator;
    }

    /**
     * run the emulator specified by the device_id
     */
    public void run(){
        ProcessBuilder processBuilder = new ProcessBuilder(emulator_location, "-avd", device_id);
        try {
            Process  process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String readline;
            try (FileWriter writer = new FileWriter("emulator.log")) {
                try {
                    while ((readline = reader.readLine()) != null ) {
                        writer.append(readline);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start () {
        if (thread == null) {
            thread = new Thread (this);
            thread.start ();
        }
    }
}

