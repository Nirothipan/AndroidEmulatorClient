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

import java.io.*;
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

//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.nio.file.StandardCopyOption;


/**
 * This class creates an Android TryIt Emulator to be used as virtual device to connect to WSO2 IOT Cloud or Product-iot.
 */
public class AndroidTryIt {
    private String osSuffix;                        // OS type of the user
    private String androidSdkHome;                  // Android SDK location
    private String userHome;                        // User home location
    private String workingDirectory;                // Present working directory
    private File sdkLocationFile;                   // File to write the Android SDK location, created in workingDirectory

    /**
     * constructor to get the system specific variables
     */
    private AndroidTryIt() {
        osSuffix = System.getProperty("os.name").toLowerCase();
        userHome = System.getProperty("user.home");
        workingDirectory = System.getProperty("user.dir");

        if (osSuffix.contains("windows")) {
            osSuffix = "windows";                        // setting the osSuffix for windows
        }
        if (osSuffix.contains("mac")) {
            osSuffix = "macosx";                         // setting the osSuffix for mac
        }
        System.out.println("Detected OS " + osSuffix);
    }

    /**
     * This method does all the functions of creating an android virtual device.
     *
     * @param args commandline arguments
     */
    public static void main(String[] args) {

        AndroidTryIt tryIt = new AndroidTryIt();
        tryIt.setAndroidSDK();           //  to set the androidSdkHome variable
        tryIt.checkBuildTools();         // check for the availability of build tools in SDK location

        try {
            tryIt.startAVD();                    // Starting a new Android Virtual Device
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            tryIt.checkEmulatorBoot();           // check for the android the virtual device Emulator system boot completion
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] agents = new String[2];        // The agents type ,{ package_name , act_name }
        try {
            agents = tryIt.checkForAgent();           //  Get the Name of the agents
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Starting Agent ...");

        try {
            tryIt.startPackage(agents);               // Starts the AVD with specific agent names( wso2_iot_agent)
        } catch (IOException e) {
            e.printStackTrace();
        }

        // adb location of the SDK
        String adbLocation = tryIt.androidSdkHome + File.separator + "platform-tools" + File.separator + "adb";
        if (tryIt.osSuffix.equals("windows"))
            adbLocation += ".exe";                // for windows the file name is adb.exe
        setExectuePermission(adbLocation);                               // set the executable permission
        Process startShell = null;                                        // creating a process to start the shell
        try {
            startShell = new ProcessBuilder(adbLocation, "shell").start();     // start the shell
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Connected to device shell");
        try {
            if (startShell == null) throw new AssertionError();
            startShell.waitFor();                                                     // wait for the shell process to complete
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Good Bye!");
    }


    /**
     * This function downloads the file in the url specified to the folder specified by folderName.
     *
     * @param url        - the URL to download from
     * @param folderName - the folder location to download the files to
     */
    private static void downloadArtifacts(URL url, String folderName) {
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            rbc = Channels.newChannel(url.openStream());        // Byte channel to read from the URL specified
            fos = new FileOutputStream(folderName);             // File output stream to write to the folder specified by the folderName
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(fos!= null) {
                    fos.close();
                }
                if(rbc != null) {
                    rbc.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * If the user has the android SDK , this function get the location of it and write to
     * the file sdkLocationFile ( can be refferred later to get the location of SDK
     */
    private void setSDKPath() {

        System.out.println("Please provide android SDK location");
        String response = new Scanner(System.in).next();                                    // get the response of the user
        // Emulator location of the user provided SDK location
        String emulatorLocation = response + File.separator + "tools" + File.separator + "emulator";

        if (osSuffix.equals("windows")) {                      // windows emulator location ends with .exe
            emulatorLocation += ".bat";
        }

        // checks for the Emulator , if present user provided SDK location is validated else asked for the location again
        if ((new File(emulatorLocation).exists())) {
            try (FileWriter writer = new FileWriter(sdkLocationFile)) {
                writer.write(response);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
        } else {
            System.out.println("Invalid SDK location");
            setSDKPath();
        }
    }

    /**
     * if the user doesn't have Android ADK , this function creates a folder named android-sdk and downloads the
     * minimum tools for SDK and write the sdk-location to the file sdkLocationFile.
     */
    private void getAndroidSDK() {

        URL url = null;                                          // url pointing to particular downloads
        String folderName = null;                                // folder name to where the file is downloaded
        String androidSdkFolderName = "android-sdk";            // name of the folder created to download SDK

        // Downloading Android SDK Tools
        System.out.println("Downloading Android SDK tools...");

        /*
         * makes directory in the working folder to download SDK , if the directory cannot be made quit the program and
         * ask user to check for the available directories in the same name.
         */
        if (!new File(workingDirectory + File.separator + androidSdkFolderName).mkdir()) {
            System.out.println("Unable to make a directory named " + androidSdkFolderName + " in " + workingDirectory);
            System.out.println("Please make sure it is not available already and can be created");
            System.exit(0);
        }
        androidSdkHome = workingDirectory + File.separator + androidSdkFolderName;  // set the SDK home location
        // Make URL to download the Android SDK tools
        try {
            url = new URL("https://dl.google.com/android/repository/tools_r25.2.5-" + osSuffix + ".zip");
            folderName = "tools_r25.2.5-" + osSuffix + ".zip";
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(0);
        }
        // Download SDK tools specified in the url to the folder specified by folderName
        downloadArtifacts(url, androidSdkHome + File.separator + folderName);
        System.out.println("Configuring Android SDK tools...");
        //extracting the downloaded zip file specified by argument
        extractFolder(androidSdkHome + File.separator + folderName);

        //Downloading Android Platform Tools
        System.out.println("Downloading Android platform tools...");
        // make url to download SDK platform tools
        try {
            url = new URL("http://dl.google.com/android/repository/platform-tools_r25.0.3-" + osSuffix + ".zip");
            folderName = "platform-tools_r25.0.3-" + osSuffix + ".zip";
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(0);
        }
        // Download SDK platform specified in the url to the folder specified by folderName
        downloadArtifacts(url, androidSdkHome + File.separator + folderName);
        System.out.println("Configuring Android platform tools...");
        extractFolder(androidSdkHome + File.separator + folderName);

        // Writing SDK location to file sdkLocationFile
        try (FileWriter writer = new FileWriter(sdkLocationFile)) {
            writer.write(androidSdkHome);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * this function creates an AVD named WSO2_AVD with default cofigurations if not already present and starts
     * the AVD specified by the user
     *
     * @throws IOException process throws  if an I/O error occurs
     */
    private void startAVD() throws IOException {
        String WSO2_AVD_location = userHome + File.separator + ".android" + File.separator + "avd" + File.separator + "WSO2_AVD.avd";
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
        String emulatorLocation = androidSdkHome + File.separator + "tools" + File.separator + "emulator";
        if (osSuffix.equals("windows")) emulatorLocation += ".exe";
        setExectuePermission(emulatorLocation);
        ProcessBuilder processBuilder = new ProcessBuilder(emulatorLocation, "-list-avds");
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
     *
     * @throws IOException process throws  if an I/O error occurs
     */
    private void createAVD() throws IOException {
        System.out.println("Creating a new AVD device");

        // creates the path of executable file avdmanager
        String avdManagerPath = androidSdkHome + File.separator + "tools" + File.separator + "bin"
                + File.separator + "avdmanager";
        if (osSuffix.equals("windows")) avdManagerPath += ".bat";
        File avdManagerFile = new File(avdManagerPath);

        // creates the path of executable file android
        String andoridPath = androidSdkHome + File.separator + "tools" + File.separator + "android";
        if (osSuffix.equals("windows")) andoridPath += ".bat";
        setExectuePermission(andoridPath);                    // provides execute permission to android

        // check for the avdmanger and creates a new AVD named WSO2_AVD
        if (avdManagerFile.exists()) {
            setExectuePermission(avdManagerPath);
            // starts a process to create the WSO_AVD
            ProcessBuilder create_avd = new ProcessBuilder(avdManagerPath, "create", "avd", "-k",
                    "system-images;android-23;default;x86", "-n", "WSO2_AVD");
            create_avd.redirectInput(ProcessBuilder.Redirect.INHERIT);
            create_avd.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = create_avd.start();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        // if avamanager not available creates the avd with android executable
        else {
            // starts a process to create WSO2_AVD using android executable file
            ProcessBuilder createAvd = new ProcessBuilder(andoridPath, "create", "avd", "-n", "WSO2_AVD", "-t", "android-23");
            createAvd.redirectInput(ProcessBuilder.Redirect.INHERIT);
            createAvd.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = createAvd.start();
            // wait for the creation process to end
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }

        // location of the WSO2_AVD configuration file provided by WSO2 with ANDROID TRY IT
        String configFileLocation = workingDirectory + File.separator + "resources" + File.separator + "config.ini";
        String wso2ConfigFile = userHome + File.separator + ".android" + File.separator + "avd" + File.separator
                + "WSO2_AVD.avd" + File.separator + "config.ini";
        // replace the WSO2_AVD configuration to the AVD created
        Files.copy(Paths.get(configFileLocation), Paths.get(wso2ConfigFile), StandardCopyOption.REPLACE_EXISTING);
        startAVD();                      // starts the AVD
    }

    /**
     * this function runs the Android Emulator for the name specified by deviceId
     *
     * @param deviceId String name of the device
     * @throws IOException process throws  if an I/O error occurs
     */
    private void runEmulator(String deviceId) throws IOException {

        // mac os and windows needs hardware_Accelerated_execution_Manager
        if (osSuffix.equals("macosx") || osSuffix.equals("windows")) {
            installHAXM();
        }

        System.out.println("Starting : " + deviceId);
        startEmulator(deviceId);                        // start the Emulator specified by the deviceId
        checkCache_img(deviceId);                       // check whether cache.img file is created in AVD folder
    }

    /**
     * This function extracts the zip file specified by zipfile and deletes the zip after extraction
     *
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
                        System.exit(0);
                    }
                }
            }
            zip.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        new File(zipFile).delete();
    }

    /**
     * this function checks for the availability of android build tools to run the AVD.
     */
    private void checkBuildTools() {
        URL url = null;
        String folderName = null;
        File build_tools = new File(androidSdkHome + File.separator + "build-tools"
                + File.separator + "25.0.2");

        // download the build tools if not available
        if (!build_tools.exists()) {
            System.out.println("Downloading Android build tools...");
            try {
                url = new URL("https://dl.google.com/android/repository/build-tools_r25.0.2-" + osSuffix + ".zip");
                folderName = "build-tools_r25.0.2-" + osSuffix + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
                System.exit(0);
            }
            downloadArtifacts(url, androidSdkHome + File.separator + folderName);   // CODE
            System.out.println("Configuring Android build tools...");
            extractFolder(androidSdkHome + File.separator + folderName);
            File build_tool = new File(androidSdkHome + File.separator + "android-7.1.1");
            new File(androidSdkHome + File.separator + "build-tools").mkdirs();
            build_tool.renameTo(new File(androidSdkHome + File.separator + "build-tools" + File.separator + "25.0.2"));
        }
    }

    /**
     * this function makes the system wait until the emulator is fully booted
     * if boot process is not completed successfully, rest of the tasks won't be as intended
     *
     * @throws IOException process throws  if an I/O error occurs
     */
    private void checkEmulatorBoot() throws IOException {
        String adbLocation = androidSdkHome + File.separator + "platform-tools" + File.separator + "adb";
        if (osSuffix.equals("windows")) adbLocation += ".exe";
        setExectuePermission(adbLocation);

        BufferedReader reader;
        String readline;
        Boolean sys_boot_complete = false;
        // check for the completion of the boot process
        do {
            // process to check boot completion process
            ProcessBuilder systemBoot = new ProcessBuilder(adbLocation, "shell", "getprop", "sys.boot_completed");
            Process systemBootProcess= systemBoot.start();
            try {
                systemBootProcess.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
            reader = new BufferedReader(new InputStreamReader(systemBootProcess.getInputStream()));
            while ((readline = reader.readLine()) != null) {
                if (readline.contains("1")) sys_boot_complete = true;
            }
            reader.close();
            System.out.print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
        } while (!sys_boot_complete);
        System.out.println();
    }

    /**
     * Ask the user whether Android SDK is available and sets the SDK path if not downloads the SDK
     */
    private void setAndroidSDK() {
        sdkLocationFile = new File("sdkLocation");

        if (!(sdkLocationFile.exists() && !sdkLocationFile.isDirectory())) {
            Scanner read = new Scanner(System.in);
            System.out.println("Do you have an Android SDK installed on your computer (y/N)?: ");
            String response = read.next().toLowerCase();
            if (response.matches("y")) {
                setSDKPath();
            } else {
                getAndroidSDK();
            }
        }
        // writes the Android SDK location to sdkLocationFile file
        try {
            Scanner scanner = new Scanner(sdkLocationFile);
            androidSdkHome = scanner.useDelimiter("\\Z").next();
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * checks for the android agent in the specified AVD and installs if not available
     *
     * @return package name and act name
     * @throws IOException process throws  if an I/O error occurs
     */
    private String[] checkForAgent() throws IOException {

        String adbLocation = androidSdkHome + File.separator + "platform-tools" + File.separator + "adb";
        if (osSuffix.equals("windows")) adbLocation += ".exe";
        setExectuePermission(adbLocation);

        String apk_file_location = workingDirectory + File.separator + "resources" + File.separator + "android-agent.apk";
        String aapt_location = androidSdkHome + File.separator + "build-tools" + File.separator + "25.0.2"
                + File.separator + "aapt";
        if (osSuffix.equals("windows")) aapt_location += ".exe";
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
        ProcessBuilder list_packages = new ProcessBuilder(adbLocation, "shell", "pm", "list", "packages");
        process = list_packages.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(0);
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
     *
     * @throws IOException process throws  if an I/O error occurs
     */
    private void installAgent() throws IOException {
        String adbLocation = androidSdkHome + File.separator + "platform-tools" + File.separator + "adb";
        if (osSuffix.equals("windows")) adbLocation += ".exe";
        setExectuePermission(adbLocation);

        System.out.println("Installing agent ...");
        String android_agent_location = workingDirectory + File.separator + "resources" + File.separator + "android-agent.apk";

        // process to install agent
        ProcessBuilder install_android_agent = new ProcessBuilder(adbLocation, "install", android_agent_location);
        Process installing_android_agent = install_android_agent.start();
        try {
            installing_android_agent.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * Starts the package (wso2.iot.agent)
     *
     * @param agents package name and launchable activity name
     * @throws IOException process throws  if an I/O error occurs
     */
    private void startPackage(String[] agents) throws IOException {
        String pkg = agents[0];
        String act = agents[1];

        String adbLocation = androidSdkHome + File.separator + "platform-tools" + File.separator + "adb";
        if (osSuffix.equals("windows")) adbLocation += ".exe";
        setExectuePermission(adbLocation);
        ProcessBuilder pkg_start = new ProcessBuilder(adbLocation, "shell", "am", "start", "-n", pkg + "/" + act);
        Process pkg_start_process = pkg_start.start();

        try {
            pkg_start_process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * checks for the availability of Android Platform in SDK and it not available downloads it
     */
    private void checkForPlatform() {
        File platform = new File(androidSdkHome + File.separator + "platforms" + File.separator + "android-23");

        if (!platform.isDirectory()) {

            System.out.println("Downloading Android platform...");

            URL url = null;
            String folderName = null;
            try {
                url = new URL("https://dl.google.com/android/repository/platform-23_r03.zip");
                folderName = "platform-23_r03" + osSuffix + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
                System.exit(0);
            }
            downloadArtifacts(url, androidSdkHome + File.separator + folderName);
            System.out.println("Configuring Android Platform...");
            extractFolder(androidSdkHome + File.separator + folderName);
            new File(androidSdkHome + File.separator + "platforms").mkdir();
            new File(androidSdkHome + File.separator + "android-6.0").renameTo(new File(androidSdkHome
                    + File.separator + "platforms" + File.separator + "android-23"));
        }
    }

    /**
     * checks for the system images in the Android SDK and downloads if not available
     */
    private void checkForSystemImages() {
        File system_images = new File(androidSdkHome + File.separator + "system-images"
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
                System.exit(0);
            }
            downloadArtifacts(url, androidSdkHome + File.separator + folderName);
            System.out.println("Configuring Android system image...");
            extractFolder(androidSdkHome + File.separator + folderName);
            new File(androidSdkHome + File.separator + "system-images" + File.separator
                    + "android-23" + File.separator + "default").mkdirs();
            new File(androidSdkHome + File.separator + "x86").renameTo(new File(androidSdkHome
                    + File.separator + "system-images" + File.separator + "android-23" + File.separator + "default" + File.separator + "x86"));
        }
    }

    /**
     * Install Hardware_Accelerated Execution_Manager in mac and windows
     */
    private void installHAXM() {
        String HAXM_location = androidSdkHome + File.separator + "extras" + File.separator + "intel"
                + File.separator + "Hardware_Accelerated_Execution_Manager";
        if (!new File(HAXM_location).isDirectory()) {
            System.out.println("Downloading intel HAXM...");

            new File(HAXM_location).mkdirs();
            URL url = null;
            String folderName = null;

            // Download HAXM for specific OS
            try {
                url = new URL("https://dl.google.com/android/repository/extras/intel/haxm-" + osSuffix + "_r6_0_5.zip");
                folderName = "haxm-" + osSuffix + ".zip";
            } catch (MalformedURLException e) {
                e.printStackTrace();
                System.exit(0);
            }
            downloadArtifacts(url, HAXM_location + File.separator + folderName);
            System.out.println("Configuring HAXM...");
            extractFolder(HAXM_location + File.separator + folderName);

            String HAXM_installer = HAXM_location + File.separator + "silent_install";
            if (osSuffix.equals("windows")) HAXM_installer += ".bat";
            else HAXM_installer += ".sh";
            setExectuePermission(HAXM_installer);

            // process to install HAXM
            ProcessBuilder processBuilder = new ProcessBuilder(HAXM_installer, "-m", "2048", "-log",
                    workingDirectory + File.separator + "haxm_silent_run.log");
            processBuilder.directory(new File(HAXM_location));
            processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = null;
            try {
                process = processBuilder.start();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
            System.out.println("Please restart your machine and run again.");
            System.exit(0);
        }
    }

    /**
     * Starts the Android emulator for specific device name
     *
     * @param deviceId - name of the device to start the emulator
     */
    private void startEmulator(String deviceId) {

        // emulator location in the Android SDK
        String emulatorLocation = androidSdkHome + File.separator + "tools" + File.separator + "emulator";
        if (osSuffix.equals("windows")) emulatorLocation += ".exe";
        setExectuePermission(emulatorLocation);

        // qemu file location -- differs for different OS
        String qemu_system_file_location = androidSdkHome + File.separator + "tools" + File.separator
                + "qemu" + File.separator;
        if (osSuffix.equals("macosx")) {
            qemu_system_file_location += "darwin" + "-x86_64" + File.separator + "qemu-system-i386";
        }
        if (osSuffix.equals("linux")) {
            qemu_system_file_location += osSuffix + "-x86_64" + File.separator + "qemu-system-i386";
        }
        if (osSuffix.equals("windows")) {
            qemu_system_file_location += osSuffix + "-x86_64" + File.separator + "qemu-system-i386.exe";
        }
        setExectuePermission(qemu_system_file_location);       // set the execution persmission for quemu file

        // Starts the Emulator and log the emulator outputs to file
        AndroidTryItEmulator logFile = new AndroidTryItEmulator(deviceId, emulatorLocation);       // Run Emulator and log
        logFile.start();
    }

    /**
     * makes the system wait until the cache.img file is created for teh particular AVD
     *
     * @param deviceId - name of the AVD
     */
    private void checkCache_img(String deviceId) {
        File cache_img = new File(userHome + File.separator + ".android"
                + File.separator + "avd" + File.separator + deviceId + ".avd" + File.separator + "cache.img");
        while (!cache_img.exists()) {
            System.out.print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        System.out.println();

        // wait for 5sec to fully load the cache image
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * set the executable permission for the specified file . if the files are not the executable,
     * the process won't work
     *
     * @param fileName name of the file to set execution permission
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
