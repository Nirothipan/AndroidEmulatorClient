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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * This class creates an Android TryIt Emulator to be used as virtual device to connect to WSO2 IOT Cloud.
 */
public class AndroidTryIt {
    private static String OS_SUFFIX;                        // OS type of the user
    private static String ANDROID_TRYIT_SDK_HOME;           // Android SDK location
    private static String USER_HOME;                        // User home location
    private static String SCRIPT_HOME;                      // Present working directory
    private static File sdkLocation;                        // File to write the Android SDK location, created in SCRIPT_HOME

    /**
     * This method does all the functions of creating an android virtual device.
     * @param args commandline arguments
     */
    public static void main(String[] args) {
        //Getting system specific variables
        OS_SUFFIX = System.getProperty("os.name").toLowerCase();
        USER_HOME = System.getProperty("user.home");
        SCRIPT_HOME = System.getProperty("user.dir");

        if (OS_SUFFIX.contains("windows")) OS_SUFFIX = "windows";           // setting the OS_SUFFIX for windows
        if (OS_SUFFIX.contains("mac")) OS_SUFFIX = "macosx";                // setting the OS_SUFFIX for mac

        System.out.println("Detected OS " + OS_SUFFIX);

        setAndroidSDK();           //  to set the ANDROID_TRYIT_SDK_HOME variable
        checkBuildTools();         // check for the availability of build tools in SDK location

        try {
            startAVD();                    // Starting a new Android Virtual Device
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            checkEmulatorBoot();           // check for the android the virtual device Emulator system boot completion
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] agents = new String[2];        // The agents type ,{ package_name , act_name }
        try {
            agents = checkForAgent();           //  Get the Name of the agents
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Starting Agent ...");

        try {
            startPackage(agents);               // Starts the AVD with specific agent names( wso2_iot_agent)
        } catch (IOException e) {
            e.printStackTrace();
        }

        // adb location of the SDK
        String adb_location = ANDROID_TRYIT_SDK_HOME + File.separator + "platform-tools" + File.separator + "adb";
        if (OS_SUFFIX.equals("windows")) adb_location += ".exe";                // for windows the file name is adb.exe
        setExectuePermission(adb_location);                               // set the executable permission
        Process start_shell =null;                                        // creating a process to start the shell
        try {
            start_shell = new ProcessBuilder(adb_location, "shell").start();     // start the shell
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Connected to device shell");
        try {
            if (start_shell == null) throw new AssertionError();
            start_shell.waitFor();                                                     // wait for the shell process to complete
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Good Bye!");
    }

    /**
     * This function downloads the file in the url specified to the folder specified by folderName.
     * @param url - the URL to download from
     * @param folderName     - the folder location to download the files to
     */
    private static void downloadArtifacts(URL url, String folderName) {
        try {
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());     // Byte channel to read from the URL specified
            FileOutputStream fos = new FileOutputStream(folderName);             // File output stream to write to the folder specified by the folderName
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * If the user has the android SDK , this function get the location of it and write to
     * the file sdkLocation ( can be refferred later to get the location of SDK
     */
    private static void setSDKPath() {

        System.out.println("Please provide android SDK location");
        String response = new Scanner(System.in).next();                                    // get the response of the user
        // Emulator location of the user provided SDK location
        String emulator_location = response + File.separator + "tools" + File.separator + "emulator";

        if (OS_SUFFIX.equals("windows")) {                      // windows emulator location ends with .exe
            emulator_location += ".bat";
        }

        // checks for the Emulator , if present user provided SDK location is validated else asked for the location again
        if ((new File(emulator_location).exists())) {
            try (FileWriter writer = new FileWriter(sdkLocation)) {
                writer.write(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Invalid SDK location");
            setSDKPath();
        }
    }

    /**
     * if the user doesn't have Android ADK , this function creates a folder named android-sdk and downloads the
     * minimum tools for SDK and write the sdk-location to the file sdkLocation.
     */
    private static void getAndroidSDK() {

        URL url = null;                                          // url pointing to particular downloads
        String folderName = null;                                // folder name to where the file is downloaded
        String ANDROID_SDK_HOME_NAME = "android-sdk";            // name of the folder created to download SDK

        // Downloading Android SDK Tools
        System.out.println("Downloading Android SDK tools...");

        /*
         * makes directory in the working folder to download SDK , if the directory cannot be made quit the program and
         * ask user to check for the available directories in the same name.
         */
        if (!new File(SCRIPT_HOME+File.separator+ANDROID_SDK_HOME_NAME).mkdir()) {
            System.out.println("Unable to make a directory named " + ANDROID_SDK_HOME_NAME + " in " + SCRIPT_HOME);
            System.out.println("Please make sure it is not available already and can be created");
            System.exit(0);
        }
        ANDROID_TRYIT_SDK_HOME = SCRIPT_HOME+File.separator+ANDROID_SDK_HOME_NAME;  // set the SDK home location
        // Make URL to download the Android SDK tools
        try {
            url = new URL("https://dl.google.com/android/repository/tools_r25.2.5-" + OS_SUFFIX + ".zip");
            folderName = "tools_r25.2.5-" + OS_SUFFIX + ".zip";
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        // Download SDK tools specified in the url to the folder specified by folderName
        downloadArtifacts(url, ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
        System.out.println("Configuring Android SDK tools...");
        //extracting the downloaded zip file specified by argument
        extractFolder(ANDROID_TRYIT_SDK_HOME + File.separator + folderName);

        //Downloading Android Platform Tools
        System.out.println("Downloading Android platform tools...");
        // make url to download SDK platform tools
        try {
            url = new URL("http://dl.google.com/android/repository/platform-tools_r25.0.3-" + OS_SUFFIX + ".zip");
            folderName = "platform-tools_r25.0.3-" + OS_SUFFIX + ".zip";
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        // Download SDK platform specified in the url to the folder specified by folderName
        downloadArtifacts(url, ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
        System.out.println("Configuring Android platform tools...");
        extractFolder(ANDROID_TRYIT_SDK_HOME + File.separator + folderName);

        // Writing SDK location to file sdkLocation
        try (FileWriter writer = new FileWriter(sdkLocation)) {
            writer.write(ANDROID_TRYIT_SDK_HOME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * this function creates an AVD named WSO2_AVD with default cofigurations if not already present and starts
     * the AVD specified by the user
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void startAVD() throws IOException {
        String WSO2_AVD_location = USER_HOME + File.separator + ".android" + File.separator + "avd" + File.separator + "WSO2_AVD.avd";
        File WSO2_AVD = new File(WSO2_AVD_location);

        checkForPlatform();                       // check for the SDK platform
        checkForSystemImages();                   // check fr the SDK system images

        //check for the WS2_AVD and creates if not available
        if (!WSO2_AVD.isDirectory()) {
            Scanner read = new Scanner(System.in);
            System.out.println("Do you want to create WSO2_AVD with default configs (Y/n)?: ");
            String response = read.next().toLowerCase();
            if (response.matches("y")) {
                createAVD();                                          // create the WSO2_AVD
                return;
            }
        }
        System.out.println("+----------------------------------------------------------------+");
        System.out.println("|                        WSO2 Android Tryit                      |");
        System.out.println("+----------------------------------------------------------------+");

        // List the available AVDs in the system
        String emulator_location = ANDROID_TRYIT_SDK_HOME + File.separator + "tools" + File.separator + "emulator";
        if (OS_SUFFIX.equals("windows")) emulator_location += ".exe";
        setExectuePermission(emulator_location);
        ProcessBuilder processBuilder = new ProcessBuilder(emulator_location, "-list-avds");
        Process process = processBuilder.start();

        // store the available AVD names in devices
        ArrayList<String> devices = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String readline;
        while ((readline = reader.readLine()) != null) {
            devices.add(readline);
        }

        // Select an AVD to run
        if (devices.size() == 0) {                                  // if no AVDs allow the user to create WSO2_AVD
            System.out.println("No AVDs available in the system ");
            startAVD();                                              // call to create an AVD
        } else if (devices.size() == 1) {                           // if only one AVD is there , starts it
            runEmulator(devices.get(0));                            // runs the emulator for the AVD
        } else {
            System.out.println("\nAvailable AVDs in the system\n");     // print the available AVDs
            for (int i = 0; i < devices.size(); i++) {
                System.out.println(++i + ") " + devices.get(--i));
            }
            System.out.print("\nEnter AVD number to start (eg: 1) :");       // Asks for the user to start particular AVD
            Scanner read = new Scanner(System.in);
            int avd_no = read.nextInt();
            runEmulator(devices.get(--avd_no));                                  // run the Emulator with specific ID
        }
    }

    /**
     * If WSO2_AVD is not available , creates the WSO2_AVD with default configurations
     * default config file have to provided in the resources folder of the working directory
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void createAVD() throws IOException {
        System.out.println("Creating a new AVD device");

        // creates the path of executable file avdmanager
        String avd_managerPath = ANDROID_TRYIT_SDK_HOME + File.separator + "tools" + File.separator + "bin"
                + File.separator + "avdmanager";
        if (OS_SUFFIX.equals("windows")) avd_managerPath += ".bat";
        File avd_manager = new File(avd_managerPath);

        // creates the path of executable file android
        String andoridPath = ANDROID_TRYIT_SDK_HOME + File.separator + "tools" + File.separator + "android";
        if (OS_SUFFIX.equals("windows")) andoridPath += ".bat";
        setExectuePermission(andoridPath);                    // provides execute permission to android

        // check for the avdmanger and creates a new AVD named WSO2_AVD
        if (avd_manager.exists()) {
            setExectuePermission(avd_managerPath);
            // starts a process to create the WSO_AVD
            ProcessBuilder create_avd = new ProcessBuilder(avd_managerPath, "create", "avd", "-k",
                    "system-images;android-23;default;x86", "-n", "WSO2_AVD");
            create_avd.redirectInput(ProcessBuilder.Redirect.INHERIT);
            create_avd.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = create_avd.start();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // if avamanager not available creates the avd with android executable
        else {
            // starts a process to create WSO2_AVD using android executable file
            ProcessBuilder create_avd = new ProcessBuilder(andoridPath, "create", "avd", "-n", "WSO2_AVD", "-t", "android-23");
            create_avd.redirectInput(ProcessBuilder.Redirect.INHERIT);
            create_avd.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = create_avd.start();
            // wait for the creation process to end
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // location of the WSO2_AVD configuration file provided by WSO2 with ANDROID TRY IT
        String config_file_location = SCRIPT_HOME + File.separator + "resources" + File.separator + "config.ini";
        String WSO2_config_file = USER_HOME + File.separator + ".android" + File.separator + "avd" + File.separator
                + "WSO2_AVD.avd" + File.separator + "config.ini";
        // replace the WSO2_AVD configuration to the AVD created
        Files.copy(Paths.get(config_file_location), Paths.get(WSO2_config_file), StandardCopyOption.REPLACE_EXISTING);
        startAVD();                      // starts the AVD
    }

    /**
     * this function runs the Android Emulator for the name specified by device_id
     * @param device_id String name of the device
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void runEmulator(String device_id) throws IOException {

        // mac os and windows needs hardware_Accelerated_execution_Manager
        if (OS_SUFFIX.equals("macosx") || OS_SUFFIX.equals("windows")) {
            installHAXM();
        }

        System.out.println("Starting : " + device_id);
        startEmulator(device_id);                        // start the Emulator specified by the device_id
        checkCache_img(device_id);                       // check whether cache.img file is created in AVD folder
    }

    /**
     * This function extracts the zip file specified by zipfile and deletes the zip after extraction
     * @param zipFile String path of the zip file
     */
    private static void extractFolder(String zipFile) {
        int BUFFER = 2048;
        File file = new File(zipFile);
        ZipFile zip;
        try {
            zip = new ZipFile(file);
            String newPath = zipFile.substring(0, zipFile.lastIndexOf(File.separator));

            new File(newPath).mkdirs();

            Enumeration zipFileEntries = zip.entries();

            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(newPath, currentEntry);
                File destinationParent = destFile.getParentFile();

                if (destinationParent == null) {
                    destFile.mkdirs();
                    continue;
                } else destinationParent.mkdirs();

                if (!entry.isDirectory()) {
                    BufferedInputStream is;
                    try {
                        is = new BufferedInputStream(zip.getInputStream(entry));
                        int currentByte;
                        // establish buffer for writing file
                        byte data[] = new byte[BUFFER];

                        // write the current file to disk
                        FileOutputStream fos = new FileOutputStream(destFile);
                        BufferedOutputStream dest = new BufferedOutputStream(fos,
                                BUFFER);

                        // read and write until last byte is encountered
                        while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
                            dest.write(data, 0, currentByte);
                        }
                        dest.flush();
                        dest.close();
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            zip.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        new File(zipFile).delete();
    }

    /**
     * this function checks for the availability of android build tools to run the AVD.
     */
    private static void checkBuildTools() {
        URL url = null;
        String folderName = null;
        File build_tools = new File(ANDROID_TRYIT_SDK_HOME + File.separator + "build-tools"
                + File.separator + "25.0.2");

        // download the build tools if not available
        if (!build_tools.exists()) {
            System.out.println("Downloading Android build tools...");
            try {
                url = new URL("https://dl.google.com/android/repository/build-tools_r25.0.2-" + OS_SUFFIX + ".zip");
                folderName = "build-tools_r25.0.2-" + OS_SUFFIX + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            downloadArtifacts(url, ANDROID_TRYIT_SDK_HOME + File.separator + folderName);   // CODE
            System.out.println("Configuring Android build tools...");
            extractFolder(ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
            File build_tool = new File(ANDROID_TRYIT_SDK_HOME + File.separator + "android-7.1.1");
            new File(ANDROID_TRYIT_SDK_HOME + File.separator + "build-tools").mkdirs();
            build_tool.renameTo(new File(ANDROID_TRYIT_SDK_HOME + File.separator + "build-tools" + File.separator + "25.0.2"));
        }
    }

    /**
     * this function makes the system wait until the emulator is fully booted
     * if boot process is not completed successfully, rest of the tasks won't be as intended
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void checkEmulatorBoot() throws IOException {
        String adb_location = ANDROID_TRYIT_SDK_HOME + File.separator + "platform-tools" + File.separator + "adb";
        if (OS_SUFFIX.equals("windows")) adb_location += ".exe";
        setExectuePermission(adb_location);

        BufferedReader reader;
        String readline;
        Boolean sys_boot_complete = false;
        // check for the completion of the boot process
        do {
            // process to check boot completion process
            ProcessBuilder sys_boot_completed = new ProcessBuilder(adb_location, "shell", "getprop", "sys.boot_completed");
            Process sys_boot_process = sys_boot_completed.start();
            try {
                sys_boot_process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            reader = new BufferedReader(new InputStreamReader(sys_boot_process.getInputStream()));
            while ((readline = reader.readLine()) != null) {
                if (readline.contains("1")) sys_boot_complete = true;
            }
            reader.close();
            System.out.print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (!sys_boot_complete);
        System.out.println();
    }

    /**
     * Ask the user whether Android SDK is available and sets the SDK path if not downloads the SDK
     */
    private static void setAndroidSDK() {
        sdkLocation = new File("sdkLocation");

        if (!(sdkLocation.exists() && !sdkLocation.isDirectory())) {
            Scanner read = new Scanner(System.in);
            System.out.println("Do you have an Android SDK installed on your computer (y/N)?: ");
            String response = read.next().toLowerCase();
            if (response.matches("y")) {
                setSDKPath();
            } else {
                getAndroidSDK();
            }
        }
        // writes the Android SDK location to sdkLocation file
        try {
            Scanner scanner = new Scanner(sdkLocation);
            ANDROID_TRYIT_SDK_HOME = scanner.useDelimiter("\\Z").next();
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * checks for the android agent in the specified AVD and installs if not available
     * @return package name and act name
     * @throws IOException process throws  if an I/O error occurs
     */
    private static String[] checkForAgent() throws IOException {

        String adb_location = ANDROID_TRYIT_SDK_HOME + File.separator + "platform-tools" + File.separator + "adb";
        if (OS_SUFFIX.equals("windows")) adb_location += ".exe";
        setExectuePermission(adb_location);

        String apk_file_location = SCRIPT_HOME + File.separator + "resources" + File.separator + "android-agent.apk";
        String aapt_location = ANDROID_TRYIT_SDK_HOME + File.separator + "build-tools" + File.separator + "25.0.2"
                + File.separator + "aapt";
        if (OS_SUFFIX.equals("windows")) aapt_location += ".exe";
        setExectuePermission(aapt_location);

        //process to get the name of package and launchable-activity available in android agent apk file
        ProcessBuilder badging_apk_file = new ProcessBuilder(aapt_location, "d", "badging", apk_file_location);
        Process process = badging_apk_file.start();

        String pkg = null;
        String act = null;
        Boolean hasAgent = false;

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String readLine;
        while ((readLine = reader.readLine()) != null) {
            if (readLine.contains("package")) {
                pkg = readLine.substring(readLine.indexOf("name=") + 6).substring(0,
                        readLine.substring(readLine.indexOf("name=") + 6).indexOf("'"));
            }
            if (readLine.contains("launchable-activity")) {
                act = readLine.substring(readLine.indexOf("name=") + 6).substring(0,
                        readLine.substring(readLine.indexOf("name=") + 6).indexOf("'"));
            }
        }
        reader.close();

        // process to list the available packages in the shell
        ProcessBuilder list_packages = new ProcessBuilder(adb_location, "shell", "pm", "list", "packages");
        process = list_packages.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        while ((readLine = reader.readLine()) != null) {
            if (readLine.contains("package:" + pkg)) {
                hasAgent = true;
            }
        }
        reader.close();

        // if the available package and launchable activity doesn't match with android-agent.apk install the android agent
        if (!hasAgent) {
            installAgent();
        }
        return new String[]{pkg, act};
    }

    /**
     * installs the Android Agent
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void installAgent() throws IOException {
        String adb_location = ANDROID_TRYIT_SDK_HOME + File.separator + "platform-tools" + File.separator + "adb";
        if (OS_SUFFIX.equals("windows")) adb_location += ".exe";
        setExectuePermission(adb_location);

        System.out.println("Installing agent ...");
        String android_agent_location = SCRIPT_HOME + File.separator + "resources" + File.separator + "android-agent.apk";

        // process to install agent
        ProcessBuilder install_android_agent = new ProcessBuilder(adb_location, "install", android_agent_location);
        Process installing_android_agent = install_android_agent.start();
        try {
            installing_android_agent.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts the package (wso2.iot.agent)
     * @param agents   package name and launchable activity name
     * @throws IOException process throws  if an I/O error occurs
     */
    private static void startPackage(String[] agents) throws IOException {
        String pkg = agents[0];
        String act = agents[1];

        String adb_location = ANDROID_TRYIT_SDK_HOME + File.separator + "platform-tools" + File.separator + "adb";
        if (OS_SUFFIX.equals("windows")) adb_location += ".exe";
        setExectuePermission(adb_location);
        ProcessBuilder pkg_start = new ProcessBuilder(adb_location, "shell", "am", "start", "-n", pkg + "/" + act);
        Process pkg_start_process = pkg_start.start();

        try {
            pkg_start_process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * checks for the availability of Android Platform in SDK and it not available downloads it
     */
    private static void checkForPlatform() {
        File platform = new File(ANDROID_TRYIT_SDK_HOME + File.separator + "platforms" + File.separator + "android-23");

        if (!platform.isDirectory()) {

            System.out.println("Downloading Android platform...");

            URL url = null;
            String folderName = null;
            try {
                url = new URL("https://dl.google.com/android/repository/platform-23_r03.zip");
                folderName = "platform-23_r03" + OS_SUFFIX + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            downloadArtifacts(url, ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
            System.out.println("Configuring Android Platform...");
            extractFolder(ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
            new File(ANDROID_TRYIT_SDK_HOME + File.separator + "platforms").mkdir();
            new File(ANDROID_TRYIT_SDK_HOME + File.separator + "android-6.0").renameTo(new File(ANDROID_TRYIT_SDK_HOME
                    + File.separator + "platforms" + File.separator + "android-23"));
        }
    }

    /**
     * checks for the system images in the Android SDK and downloads if not available
     */
    private static void checkForSystemImages() {
        File system_images = new File(ANDROID_TRYIT_SDK_HOME + File.separator + "system-images"
                + File.separator + "android-23" + File.separator + "default");

        if (!system_images.isDirectory()) {
            System.out.println("Downloading Android system image...");
            URL url = null;
            String folderName = null;
            try {
                url = new URL("https://dl.google.com/android/repository/sys-img/android/x86-23_r09.zip");
                folderName = "sys-img" + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            downloadArtifacts(url, ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
            System.out.println("Configuring Android system image...");
            extractFolder(ANDROID_TRYIT_SDK_HOME + File.separator + folderName);
            new File(ANDROID_TRYIT_SDK_HOME + File.separator + "system-images" + File.separator
                    + "android-23" + File.separator + "default").mkdirs();
            new File(ANDROID_TRYIT_SDK_HOME + File.separator + "x86").renameTo(new File(ANDROID_TRYIT_SDK_HOME
                    + File.separator + "system-images" + File.separator + "android-23" + File.separator + "default" + File.separator + "x86"));
        }
    }

    /**
     * Install Hardware_Accelerated Execution_Manager in mac and windows
     */
    private static void installHAXM() {
        String HAXM_location = ANDROID_TRYIT_SDK_HOME + File.separator + "extras" + File.separator + "intel"
                + File.separator + "Hardware_Accelerated_Execution_Manager";
        if (!new File(HAXM_location).isDirectory()) {
            System.out.println("Downloading intel HAXM...");

            new File(HAXM_location).mkdirs();
            URL url = null;
            String folderName = null;

            // Download HAXM for specific OS
            try {
                url = new URL("https://dl.google.com/android/repository/extras/intel/haxm-" + OS_SUFFIX + "_r6_0_5.zip");
                folderName = "haxm-" + OS_SUFFIX + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            downloadArtifacts(url, HAXM_location + File.separator + folderName);
            System.out.println("Configuring HAXM...");
            extractFolder(HAXM_location + File.separator + folderName);

            String HAXM_installer = HAXM_location + File.separator + "silent_install";
            if (OS_SUFFIX.equals("windows")) HAXM_installer += ".bat";
            else HAXM_installer += ".sh";
            setExectuePermission(HAXM_installer);

            // process to install HAXM
            ProcessBuilder processBuilder = new ProcessBuilder(HAXM_installer, "-m", "2048", "-log",
                    SCRIPT_HOME + File.separator + "haxm_silent_run.log");
            processBuilder.directory(new File(HAXM_location));
            processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = null;
            try {
                process = processBuilder.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (null != process) {
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Please restart your machine and run again.");
            System.exit(0);
        }
    }

    /**
     * Starts the Android emulator for specific device name
     * @param device_id - name of the device to start the emulator
     */
    private static void startEmulator(String device_id) {

        // emulator location in the Android SDK
        String emulator_location = ANDROID_TRYIT_SDK_HOME + File.separator + "tools" + File.separator + "emulator";
        if (OS_SUFFIX.equals("windows")) emulator_location += ".exe";
        setExectuePermission(emulator_location);

        // qemu file location -- differs for different OS
        String qemu_system_file_location = ANDROID_TRYIT_SDK_HOME + File.separator + "tools" + File.separator
                + "qemu" + File.separator;
        if (OS_SUFFIX.equals("macosx")) {
            qemu_system_file_location += "darwin" + "-x86_64" + File.separator + "qemu-system-i386";
        }
        if (OS_SUFFIX.equals("linux")) {
            qemu_system_file_location += OS_SUFFIX + "-x86_64" + File.separator + "qemu-system-i386";
        }
        if (OS_SUFFIX.equals("windows")) {
            qemu_system_file_location += OS_SUFFIX + "-x86_64" + File.separator + "qemu-system-i386.exe";
        }
        setExectuePermission(qemu_system_file_location);       // set the execution persmission for quemu file

        // Starts the Emulator and log the emulator outputs to file
        RunEmulator logFile = new RunEmulator(device_id, emulator_location);       // Run Emulator and log
        logFile.start();
    }

    /**
     * makes the system wait until the cache.img file is created for teh particular AVD
     * @param device_id - name of the AVD
     */
    private static void checkCache_img(String device_id) {
        File cache_img = new File(System.getProperty("user.home") + File.separator + ".android"
                + File.separator + "avd" + File.separator + device_id + ".avd" + File.separator + "cache.img");
        while (!cache_img.exists()) {
            System.out.print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println();

        // wait for 5sec to fully load the cache image
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * set the executable permission for the specified file . if the files are not the executable,
     * the process wot't work
     * @param fileName name os the file to set execution permission
     */
    private static void setExectuePermission(String fileName) {
        if (!new File(fileName).canExecute()) {
            if (!new File(fileName).setExecutable(true)) {
                System.out.println("Set the Execute permission of : " + fileName);
                System.exit(0);
            }
        }
    }
}
