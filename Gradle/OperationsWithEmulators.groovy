import groovy.io.FileType
import org.codehaus.groovy.runtime.StackTraceUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

//region Tasks

//region Tasks for installing emulators
/**
 * The first step of emulators creation.
 * 1. Checking of file repositories.cfg in the directory ".android",
 * creating of this file (if it is not exist) and writing some text into it.
 * 2. Updating of SDK licenses.
 * 3. Getting of emulators list, which was selected as target devices by statistics of user devices.
 * 4. The first starting of SDK installation.
 *
 * Note: It is necessary to divide SDK installation on two steps,
 * because the first installation process creates an instalator,
 * and the second completes unzipping and installation.
 * Only one process can't accomplish installation of SDK.
 * Unfortunately, SDK manager can't accomplish both steps of SDK system image installation in a loop
 */
class InitSdkInstallation extends DefaultTask{
    @TaskAction
    def execute(){
        SDKManager.checkFileRepositoriesCfg()
        SDKManager.updateSdkLicenses()

        AVDManager.listOfEmulatorsParameters().each {

            SDKManager.installSystemImage(it.sdkVersion, true)
        }
    }
}

/**
 * The second step of emulators creation.
 * 1. Getting of emulators list, which was selected as target devices by statistics of user devices.
 * 2. Completing of SDK installation.
 */
class InvokePreparedSdkInstallation extends DefaultTask{
    @TaskAction
    def execute(){
        AVDManager.listOfEmulatorsParameters().each {

            SDKManager.installSystemImage(it.sdkVersion)
        }
    }
}

/**
 * Returns quantity of elements from emulators list, which was selected as target devices
 */
class EmulatorsQuantity extends DefaultTask{
    @TaskAction
    def execute(){
        println(AVDManager.quantityCyclesOfInstalling(false, true))
    }
}

/**
 * Creating ONE emulator from the list.
 * @Input emulatorIndexOnList(String): STRING value of index of an element on the List of emulators.
 * Note: It is necessary to call this method as many times as there are emulators to create.
 * Unfortunately, AVD manager can't create emulators in a loop. */
class CreateEmulator extends DefaultTask{
    @Input
    String emulatorIndexOnList = "0"

    @TaskAction
    def execute(){
        AVDManager.createDevice(
                AVDManager.listOfEmulatorsParameters()[emulatorIndexOnList.toInteger()])
    }
}

/**
 * Final step of one or more emulator creation:
 * checking of it/their installation, setting it/their parameters and disable animation.
 * @Input emulatorIndexOnList(String):
 *  - if it is necessary to execute operations for one emulator,
 *  than emulatorIndexOnList is a STRING value of index of one element on the List of emulators;
 *  - if it is necessary to execute for all emulators,
 *  than emulatorIndexOnList = "all"
 *
 * 1. Checking of the emulator installation.
 * 2. Writing an emulator parameters into its file "config.ini".
 * 3. Preliminary shutdown of all running emulators.
 * 4. Starting the emulator and waiting for its OS boot.
 * 5. Disabling animation on the running emulator and final shutdown of it.
 */
class SetEmulatorSettings extends DefaultTask{
    @Input
    String emulatorIndexOnList = "all"

    @TaskAction
    def execute(){
        ADB.turnOffDevices()

        if(emulatorIndexOnList == "all"){
            AVDManager.listOfEmulatorsParameters().each {
                AVDManager.isEmulatorInstalled(it.emulatorName, true)
                Files.setEmulatorParameters(it)
                Emulator.startDevice(it.emulatorName)
                ADB.waitForDeviceBoot()
                ADB.disableAnimation()
                ADB.turnOffDevices()
            }
        }else{
            def emulatorParameters =
                    AVDManager.listOfEmulatorsParameters()[emulatorIndexOnList.toInteger()]

            AVDManager.isEmulatorInstalled(emulatorParameters.emulatorName, true)
            Files.setEmulatorParameters(emulatorParameters)
            Emulator.startDevice(emulatorParameters.emulatorName)
            ADB.waitForDeviceBoot()
            ADB.disableAnimation()
            ADB.turnOffDevices()
        }
    }
}

//endregion

//region Task for tests executions
/**
 * Starting an emulator according its name and waiting for the its OS boot.
 * @Input emulatorName (String).
 * Template: "${height}x${width}_${diagonal}inch_${density}dpi"
 * Example: "1080x2340_6.53inch_400dpi"
 * Warning! The diagonal must contain dot(.), but not comma(,)
 */
class StartEmulator extends DefaultTask{
    @Input
    String emulatorName = ""

    @TaskAction
    def execute(){
        AVDManager.isEmulatorInstalled(emulatorName, true)
        Emulator.startDevice(emulatorName)
        ADB.waitForDeviceBoot()
    }
}

/**
 * Shutdown of all running emulators.
 */
class TurnOffAllEmulators extends DefaultTask{
    @TaskAction
    def execute() {
        ADB.turnOffDevices()
    }
}
//endregion
//endregion

//region Task classes

class ADB{

    private static Integer offlineDevicesCounter = 0

    /**
     * Get list of running devices
     * @param printOnly (Boolean). True: if necessary only to print list of devices.
     *                             False: if necessary to print and to return list of devices.
     * @param killOfflineDevices (Boolean). True: if necessary to kill processes that relates to
     *                                            devices with status "offline", otherwise - False.
     * @return Map of 2 lists:
     * - devicesRunning(List<String>): running emulators (example: "emulator-5554")
     * - devicesOffline(List<String>): offline emulators (example: "emulator-5554")
     */
    static def mapOfDevices(printOnly = true){
        def emulatorList = ("cmd /c " + Const.paths().pathToADB +
                        Const.cmd().adbExecEmulatorList).execute().text

        if(printOnly) Utils.logIt(Utils.currentMethodData, emulatorList)

        List<String> devicesRunning = []
        List<String> devicesOffline = []
        emulatorList.eachLine {
            if(it.contains('emulator') && !printOnly){
                if(it.contains('device')){
                    Integer endIndex = it.indexOf('\tdevice')
                    devicesRunning.add(it.substring(0, endIndex))
                }
                if(it.contains('offline')){
                    Integer endIndex = it.indexOf('\toffline')
                    devicesOffline.add(it.substring(0, endIndex))
                }
            }
        }

        Map mapOfDevices = [
                devicesRunning: devicesRunning,
                devicesOffline: devicesOffline]

        return mapOfDevices
    }

    /**
     * Wait while system properties get their values,
     * that they must have when emulator booting is completed.
     */
    static def waitForDeviceBoot(){
        ("cmd /c " + Const.paths().pathToADB + Const.cmd().adbExecWaitDevice).execute()

        Const.sysProperties().each {
            String sysPropValue = ""
            Integer counter = 0

            while(!sysPropValue.contains(it[1])){
                def process = ("cmd /c " + Const.paths().pathToADB + it[0]).execute()
                sysPropValue = process.text
                counter ++
                if(counter >= 100) break
            }

            def status = (sysPropValue.contains(it[1]) && counter < 100) ? "OK" : "NOT OK"
            Utils.logIt(Utils.currentMethodData, "Device booting check: ${it[0]} is ${status}")
        }
    }

    /**
     * Shutdown all running and offline devices.
     */
    static def turnOffDevices(){
        Map devicesMap = mapOfDevices(false)

        if(devicesMap.devicesOffline.size() != 0){
            Utils.logIt(Utils.currentMethodData,
               """WARNING!!!
               |More than one emulator launched and some devices are in the status Offline!
               |Trying to kill processes that are associated with these devices...""".stripMargin())

            devicesMap.devicesOffline.each {
                Utils.logIt(Utils.currentMethodData, "Shutting down emulator-offline: ${it}")
                shutDownOfflineDevices(it)
                sleep(1000)
            }
            offlineDevicesCounter ++
            if(offlineDevicesCounter <= 5 && devicesMap.devicesOffline.size() != 0){
                turnOffDevices()
            }else{
                mapOfDevices()
            }
        }

        if(devicesMap.devicesRunning.size() != 0){
            devicesMap.devicesRunning.each {
                Utils.logIt(Utils.currentMethodData, "Shutting down the emulator: ${it}")
                ("cmd /c " + Const.paths().pathToADB + Const.cmd().adbExecEmulatorOff_adb + it +
                        Const.cmd().adbExecEmulatorOff_shell).execute()
                sleep(5000)
            }
            sleep(5000)
            mapOfDevices()
        }
    }

    /**
     * Kill system process that is associated with an offline device.
     * @param device(String): offline emulator with port number, for example "emulator-5554"
     */
    static def shutDownOfflineDevices(String device){

        String port = device.replaceAll("emulator-", "")

        def netList = Const.cmd().cmdNetstat.execute().text.split("\\r\\n  TCP    ")

        List prcessesPID = []
        netList.each {
            if(it.contains(":${port}") && it.contains("ESTABLISHED")){
                Integer startIndex = it.indexOf("ESTABLISHED     ") + "ESTABLISHED     ".size()
                prcessesPID.add(it.substring(startIndex))
            }
        }
        if(prcessesPID.size() !=0){
            prcessesPID.unique().each {
                Utils.logIt(Utils.currentMethodData, "Running \"taskkill PID ${it}\"")
                (Const.cmd().cmdTaskKill.replaceAll("PID_NUMBER", it)).execute()
            }
        }
    }

    /**
     * Change status into "Off" for animation parameters:
     *  - window_animation_scale,
     *  - transition_animation_scale,
     *  - animator_duration_scale.
     * It is necessary during screenshot testing for
     * disable animation of such UI elements as Loader.
     */
    static def disableAnimation(){

        def animList = []
        animList << ["window_animation_scale",
                     Const.cmd().adbShellDisableAnimWindowPut,
                     Const.cmd().adbShellDisableAnimWindowGet]

        animList << ["transition_animation_scale",
                     Const.cmd().adbShellDisableAnimTransitPut,
                     Const.cmd().adbShellDisableAnimTransitGet]

        animList << ["animator_duration_scale",
                     Const.cmd().adbShellDisableAnimDurationPut,
                     Const.cmd().adbShellDisableAnimDurationGet]

        Boolean animDisabled = true
        animList.each {
            ("cmd /c " + Const.paths().pathToADB + it[1]).execute()
            sleep(3000)

            if(("cmd /c " + Const.paths().pathToADB + it[2]).execute().text.contains("0.0")){
                Utils.logIt(Utils.currentMethodData, "${it[0]} is disabled")
            }else{
                Utils.logIt(Utils.currentMethodData, "${it[0]} is enabled")
                animDisabled = false
            }
        }

        if(!animDisabled){
            Utils.logIt(Utils.currentMethodData,
                    "Animation is not disabled! For more information see above", true)
        }
    }
}

class Emulator{

    /**
     * Starting an emulator according its name and waiting for the its OS boot.
     * @param emulatorName  (String):
     *                              Template: "${height}x${width}_${diagonal}inch_${density}dpi"
     *                              Example: "1080x2340_6.53inch_400dpi"
     * Warning! The diagonal must contain dot(.), but not comma(,)
     */
    static def startDevice(String emulatorName){
        ("cmd /c " + Const.paths().pathToEmulatorExec + Const.cmd().emulatorExec +
                emulatorName).execute()

        Integer waitingTime = 0
        while (ADB.mapOfDevices(false).devicesRunning.size() == 0){
            sleep(1000)
            waitingTime += 1000

            if(waitingTime >= 120000){
                Utils.logIt(Utils.currentMethodData,
                        "The emulator had not started!", true)
                break
            }
        }
    }
}

class AVDManager{

    /**
     * Creating emulator according to it parameters.
     * @param emulatorParameters(Map):
     *  - emulatorName (String): for example "1080x2340_6.53inch_400dpi",
     *  - sdkVersion (String): for example "android-30",
     *  - density (Integer): one of values standard Android density in dpi, for example 400,
     *  - height (Integer): screen height in pixels, for example 2340,
     *  - width (Integer): screen width in pixels, for example 1080
     *  The Map of emulator parameters are created in emulatorParameters()
     */
    static def createDevice(Map emulatorParameters){

        String fileName = emulatorParameters.emulatorName + ".ini"

        if(!isEmulatorInstalled(emulatorParameters.emulatorName)){
            if(!emulatorParameters.emulatorName?.empty && !emulatorParameters.sdkVersion?.empty){

                String parameters =
                        Const.cmd().avdManagerCreate_param.replaceAll("SDK_VERSION",
                                                                      emulatorParameters.sdkVersion)

                ("cmd /c echo n| " + Const.paths().pathToAVDManager +
                        Const.cmd().avdManagerCreate_exec + emulatorParameters.emulatorName +
                        parameters).execute()

                Integer counter = 0
                while(!Files.fileExists(Const.paths().pathToAVDDirectory, fileName, FileType.FILES)){
                    counter ++
                    if(counter >= 10000) break
                }

            }else{
                String text = """The emulator name or SDK version are empty!
                          |This parameters must be inputted in the Gradle Task.""".stripMargin()
                Utils.logIt(Utils.currentMethodData, text, true)
            }
        }
    }

    /**
     * Get list of maps, which contain emulators parameters for devices that was selected as
     * target devices by statistics of user devices.
     * @return List of Maps
     */
    static def listOfEmulatorsParameters(){

        def listOfEmulatorsParameters = []

        listOfEmulatorsParameters << emulatorParameters(
                2340, 1080, 6.53, "android-29")
        /*
        listOfEmulatorsParameters << emulatorParameters(
                2400, 1080, 6.5, "android-28")
        listOfEmulatorsParameters << emulatorParameters(
                1600, 720, 6.53, "android-29")
         */

        return listOfEmulatorsParameters
    }

    /**
     * Get Map of parameters for an emulator according inputs
     * @param height(Integer): screen height in pixels, for example 2340,
     * @param width(Integer): screen width in pixels, for example 1080,
     * @param diagonal(Float): screen diagonal in inches, for example 6.53,
     * @param sdkVersion(String): for example "android-30",
     * @return Map:
     *  - emulatorName (String): for example "1080x2340_6.53inch_400dpi",
     *  - sdkVersion (String): equal input value,
     *  - density (Integer): one of values standard Android density in dpi, for example 400,
     *  - height (Integer): equal input value,
     *  - width (Integer): equal input value
     */
    static def emulatorParameters(Integer height, Integer width, Float diagonal, String sdkVersion){

        def density = getDensityByDiagonal(height, width, diagonal)
        def emulatorName = "${height}x${width}_${diagonal}inch_${density}dpi".toString()

        Map emulatorParameters = [
                emulatorName: emulatorName,
                sdkVersion: sdkVersion,
                density: density,
                height: height,
                width: width]

        return emulatorParameters
    }

    /**
     * Get quantity of cycles for installing SDK versions  or Emulators
     * @param sdks(Boolean): True - for SDK installation, otherwise - False
     * @param emulators(Boolean): True - for Emulators installation, otherwise - False
     * @return Integer
     */
    static def quantityCyclesOfInstalling(Boolean sdks = false, Boolean emulators = false){

        Integer quantity = 0
        def parametersLists = listOfEmulatorsParameters()
        List sdkList = []

        if(sdks){
            parametersLists.each {
                sdkList << it.sdkVersion
            }
            quantity = sdkList.unique().size()

        }else if(emulators){
            quantity = parametersLists.size()
        }

        return quantity
    }

    /**
     * Check the emulator installation and return result
     * @param emulatorName(String): for example "2340x1080_6.53inch_400dpi"
     * @param callException(Boolean): True - if necessary to call Exception, otherwise - false
     * @return Boolean: result of checking
     */
    static def isEmulatorInstalled(String emulatorName, Boolean callException = false){

        String fileName = emulatorName + ".ini"

        if(Files.fileExists(Const.paths().pathToAVDDirectory, fileName, FileType.FILES)){
            String text = """An emulator with name ${emulatorName}
                       |already exists in the directory ${Const.paths().pathToAVDDirectory}.
                       |The CREATION of this emulator has been CANCELED!""".toString().stripMargin()
            Utils.logIt(Utils.currentMethodData, text)
            return true

        }else {
            if(callException){
                String text = """An emulator with name ${emulatorName}
                            |is not installed!!!""".toString().stripMargin()
                Utils.logIt(Utils.currentMethodData, text, callException)
            }
            return false
        }
    }

    /**
     * Calculate density by screen dimensions (diagonal, height and width)
     * and get the closest standard Android density
     * @param height(Integer): screen height in pixels, for example 2340,
     * @param width(Integer): screen width in pixels, for example 1080,
     * @param diagonal(Float): screen diagonal in inches, for example 6.53
     * @return Integer: standard Android density in dpi, for example 400
     */
    private static def getDensityByDiagonal(Integer height, Integer width, Float diagonal){
        double aspectRatio = height / width
        double widthInch = diagonal / Math.sqrt(Math.pow(aspectRatio, 2) + 1)
        int calculatedDensity = Math.round(width.toFloat() / widthInch)

        def differences = []
        standardDensities().each {
            differences << Math.abs(it - calculatedDensity)
        }

        return standardDensities()[differences.findIndexOf{it == differences.min()}]
    }

    /**
     * Get constant list of standard Android densities
     * @return List<Integer>
     */
    private static def standardDensities(){
        def standardDensities = [120, 140, 160, 180,
                                 213, 240, 280,
                                 320, 340, 360,
                                 400, 420, 440, 480,
                                 560, 640]
        return standardDensities
    }
}

class SDKManager{

    /**
     * Checking of file repositories.cfg in the directory ".android",
     * creating of this file (if it is not exist) and writing some text into it.
     */
    static def checkFileRepositoriesCfg(){
        if(Files.fileExists(Const.paths().pathToRepositoriesCfg,
                            "repositories.cfg", FileType.FILES)){
            Utils.logIt(Utils.currentMethodData, "Check SUCCESSFUL")
        } else {
            new File(Const.paths().pathToRepositoriesCfg + "repositories.cfg").text =
                    """### User Sources for Android SDK Manager
                    |#Fri Nov 03 10:11:27 CET 2017 count=0 """.stripMargin()
            Utils.logIt(Utils.currentMethodData,
                    "File .android\\repositories.cfg was successfully CREATED")
        }
    }

    /**
     * Updating of SDK licenses
     */
    static def updateSdkLicenses(){

        ("cmd /c echo y| " + Const.paths().pathToSDKManager +
                Const.cmd().sdkManagerLicenses).execute()
        Utils.logIt(Utils.currentMethodData, "SDK licenses updated")
    }

    /**
     * Install SDK of a specific version.
     * Type of SDK and its bitness are constant: "google_apis" and "x86_64".
     * @param sdkVersion(String): for example "android-30".
     * @param isFirstExecution(Boolean): True - if it is the first starting of SDK installation
     *                                 (then max time of waiting for process complete will be less),
     *                                 False - if it is the second starting of SDK installation
     */
    static def installSystemImage(String sdkVersion, Boolean isFirstExecution = false){
        String sdk = "google_apis"
        String bitness = "x86_64"
        String packageSdk = "system-images;${sdkVersion};${sdk};${bitness}"
        Integer maxWaitingTime = 1200000 //SDK manager returns Fail after 20 minutes installation

        if(isFirstExecution) maxWaitingTime = 10000

        if(isSdkPackageInstalled(sdkVersion, sdk, bitness)){
            Utils.logIt(Utils.currentMethodData,
                    "System image ${sdkVersion} is already installed")
        }else{

            installSDK(packageSdk)
            waitForSdkInstalling(sdkVersion, sdk, bitness, maxWaitingTime)

            if(isSdkPackageInstalled(sdkVersion, sdk, bitness)){
                Utils.logIt(Utils.currentMethodData,
                        "System image \"${packageSdk}\" successfully installed")
            }else{
                if(isFirstExecution){
                    Utils.logIt(Utils.currentMethodData,
                            """System image \"${packageSdk}\" is not installed.
                              |Installing will be automated restarted""".toString().stripMargin())
                }else{
                Utils.logIt(Utils.currentMethodData,
                        """System image \"${packageSdk}\" is not installed!
                        |Execute \"${Const.paths().pathToSDKManager}sdkmanager --list\" in cmd and
                        |verify \"${packageSdk}\" in the list""".toString().stripMargin(),
                        true)}
            }
        }
    }

    /**
     * Install SDK of a specific package.
     * @param packageSdk(String):
     *                          Template: "system-images;${sdkVersion};${sdk};${bitness}"
     *                          Example: "system-images;android-29;google_apis;x86_64"
     */
    static void installSDK(String packageSdk){

        ("cmd /c " + Const.paths().pathToSDKManager + Const.cmd().sdkManagerInstall +
                "\"${packageSdk}\"").execute()
    }

    /**
     * Check if SDK is installed.
     * @param sdkVersion(String): for example "android-30",
     * @param sdk(String): for example "google_apis",
     * @param bitness(String): for example "x86_64".
     * @return Boolean
     */
    static def isSdkPackageInstalled(String sdkVersion, String sdk, String bitness){

        return (Files.fileExists(
        "${Const.paths().pathToSDKSystemImages}\\",
                    sdkVersion, FileType.DIRECTORIES) &&

        Files.fileExists(
        "${Const.paths().pathToSDKSystemImages}\\${sdkVersion}\\",
                    sdk, FileType.DIRECTORIES) &&

        Files.fileExists(
        "${Const.paths().pathToSDKSystemImages}\\${sdkVersion}\\${sdk}\\",
                    bitness, FileType.DIRECTORIES) &&

        Files.fileExists(
        "${Const.paths().pathToSDKSystemImages}\\${sdkVersion}\\${sdk}\\${bitness}\\",
            "system.img", FileType.FILES))
    }

    /**
     * Wait while the installation process is complete.
     * @param sdkVersion(String): for example "android-30",
     * @param sdk(String): for example "google_apis",
     * @param bitness(String): for example "x86_64",
     * @param maxWaitingTime(Integer): max time of waiting for process complete in milliseconds.
     */
    static def waitForSdkInstalling(String sdkVersion,
                                    String sdk,
                                    String bitness,
                                    Integer maxWaitingTime){

        Integer waitingTime = 0
        Integer sleepTime = 5000

        while (!isSdkPackageInstalled(sdkVersion, sdk, bitness)){
            sleep(sleepTime)
            waitingTime += sleepTime
            if(waitingTime >= maxWaitingTime) break
        }

        //this sleep is necessary, because some files in SDK package folder may created too slowly
        if(isSdkPackageInstalled(sdkVersion, sdk, bitness)) sleep(90000)

        Utils.logIt(Utils.currentMethodData, "Time of SDK installing is ${waitingTime}")
    }

}

class Files{

    /**
     * Check file exist.
     * @param pathToDirectory(String)
     * @param fileName(String)
     * @param fileType(FileType): for example FileType.FILES
     * @return Boolean
     */
    static def fileExists(String pathToDirectory, String fileName, FileType fileType){
        Boolean fileExists = false
        def directory = new File(pathToDirectory)

        directory.eachFile(fileType){
            if(it.name == fileName) fileExists = true
        }

        return fileExists
    }

    /**
     * Writing the emulator parameters into file "config.ini".
     * @param emulatorParameters(Map):
     *  - emulatorName (String): for example "1080x2340_6.53inch_400dpi",
     *  - sdkVersion (String): for example "android-30",
     *  - density (Integer): one of values standard Android density in dpi, for example 400,
     *  - height (Integer): screen height in pixels, for example 2340,
     *  - width (Integer): screen width in pixels, for example 1080
     *  The Map of emulator parameters are created in emulatorParameters()
     */
    static def setEmulatorParameters(Map emulatorParameters){

        String pathToConfigFile = Const.paths().pathToConfigFile.replaceAll("EMULATOR_NAME",
                                                                    emulatorParameters.emulatorName)
        File file = new File(pathToConfigFile)

        if((file.text =~ /AvdId/).size() == 0 && (file.text =~ /avd.ini.displayname/).size() == 0){
            def imageSysdir =
                    "system-images\\${emulatorParameters.sdkVersion}\\google_apis\\x86_64\\\n"

            file.eachLine {
                if(it.contains('image.sysdir.1=')) imageSysdir = it
            }

            file.text = imageSysdir
            file.append(configIniContent(emulatorParameters))

            Utils.logIt(Utils.currentMethodData,
                    "Emulator parameters were wrote in file ${pathToConfigFile}")
        }else{
            Utils.logIt(Utils.currentMethodData,
                    "The emulator and a config file ${pathToConfigFile} already exist!")
        }
    }

    /**
     * Create string with emulator parameters from input Map
     * @param emulatorParameters(Map):
     *  - emulatorName (String): for example "1080x2340_6.53inch_400dpi",
     *  - sdkVersion (String): for example "android-30",
     *  - density (Integer): one of values standard Android density in dpi, for example 400,
     *  - height (Integer): screen height in pixels, for example 2340,
     *  - width (Integer): screen width in pixels, for example 1080
     *  The Map of emulator parameters are created in emulatorParameters()
     * @return String
     */
    static def configIniContent(Map emulatorParameters){
        return """\nAvdId=${emulatorParameters.emulatorName}
        |PlayStore.enabled=false
        |abi.type=x86_64
        |avd.ini.displayname=${emulatorParameters.emulatorName}
        |avd.ini.encoding=UTF-8
        |disk.dataPartition.size=6G
        |fastboot.chosenSnapshotFile=
        |fastboot.forceChosenSnapshotBoot=no
        |fastboot.forceColdBoot=no
        |fastboot.forceFastBoot=yes
        |hw.accelerometer=yes
        |hw.arc=false
        |hw.audioInput=yes
        |hw.battery=yes
        |hw.camera.back=none
        |hw.camera.front=none
        |hw.cpu.arch=x86_64
        |hw.cpu.ncore=4
        |hw.dPad=no
        |hw.device.manufacturer=User
        |hw.device.name=${emulatorParameters.emulatorName}
        |hw.gps=yes
        |hw.gpu.enabled=yes
        |hw.gpu.mode=software
        |hw.initialOrientation=Portrait
        |hw.keyboard=yes
        |hw.keyboard.lid=yes
        |hw.lcd.density=${emulatorParameters.density}
        |hw.lcd.height=${emulatorParameters.height}
        |hw.lcd.width=${emulatorParameters.width}
        |hw.mainKeys=yes
        |hw.ramSize=1536
        |hw.sdCard=yes
        |hw.sensors.orientation=yes
        |hw.sensors.proximity=yes
        |hw.trackBall=no
        |runtime.network.latency=none
        |runtime.network.speed=full
        |sdcard.size=512M
        |showDeviceFrame=no
        |skin.dynamic=yes
        |skin.name=${emulatorParameters.width}x${emulatorParameters.height}
        |skin.path=_no_skin
        |skin.path.backup=_no_skin
        |tag.display=Google APIs
        |tag.id=google_apis
        |vm.heapSize=256""".toString().stripMargin()
    }
}

class Const{

    /**
     * Constant values of paths to directories and files
     * @return Map
     */
    static def paths(){
        def homePath = System.getenv('homepath')
        Map paths = [
            pathToEmulatorExec: "${homePath}\\AppData\\Local\\Android\\Sdk\\emulator\\",
            pathToADB: "${homePath}\\AppData\\Local\\Android\\Sdk\\platform-tools\\",
            pathToAVDManager: "${homePath}\\AppData\\Local\\Android\\Sdk\\cmdline-tools\\latest\\bin\\",
            pathToAVDDirectory: "${homePath}\\.android\\avd\\",
            pathToConfigFile: "${homePath}\\.android\\avd\\EMULATOR_NAME.avd\\config.ini",
            pathToSDKSystemImages: "${homePath}\\AppData\\Local\\Android\\Sdk\\system-images",
            pathToSDKManager: "${homePath}\\AppData\\Local\\Android\\Sdk\\cmdline-tools\\latest\\bin\\",
            pathToRepositoriesCfg: "${homePath}\\.android\\"
        ]
        return paths
    }

    /**
     * Constant values of CMD commands for using of SDK tools
     * @return Map
     */
    static def cmd(){
        return [
            avdManagerCreate_exec: "avdmanager -s create avd -n ",
            avdManagerCreate_param: " -k \"system-images;SDK_VERSION;google_apis;x86_64\"",
            emulatorExec: "emulator -avd ",
            adbExecEmulatorList: "adb devices",
            adbExecEmulatorOff_adb: "adb -s ",
            adbExecEmulatorOff_shell: " shell reboot -p",
            adbExecDevice: "adb wait-for-device",
            adbShellDisableAnimWindowPut:
                "adb shell settings put global window_animation_scale 0.0",
            adbShellDisableAnimWindowGet:
                "adb shell settings get global window_animation_scale",
            adbShellDisableAnimTransitPut:
                "adb shell settings put global transition_animation_scale 0.0",
            adbShellDisableAnimTransitGet:
                "adb shell settings get global transition_animation_scale",
            adbShellDisableAnimDurationPut:
                "adb shell settings put global animator_duration_scale 0.0",
            adbShellDisableAnimDurationGet:
                "adb shell settings get global animator_duration_scale",
            sdkManagerInstall: "sdkmanager ",
            sdkManagerLicenses: "sdkmanager --licenses",
            cmdNetstat: "cmd /c netstat -a -n -o",
            cmdTaskKill: "cmd /c taskkill /PID PID_NUMBER /F",
            runTests: "gradlew MODULE_NAME:connectedAndroidTest"
        ]
    }

    /**
     * Lists contain pairs of system property and its value when emulator booting is completed.
     * @return List of lists
     */
    static def sysProperties(){
        List list = []
        String getprop = "adb shell getprop"
        list << ["${getprop} init.svc.bootanim", "stopped"]
        list << ["${getprop} dev.bootcomplete", "1"]
        list << ["${getprop} vendor.qemu.dev.bootcomplete", "1"]
        list << ["${getprop} sys.user.0.ce_available", "true"]
        list << ["${getprop} sys.wifitracing.started", "1"]
        list << ["${getprop} selinux.restorecon_recursive", "/data/misc_ce/0"]

        return list
    }
}

class Utils{

    /**
     * Send message to user in the Build window.
     * @param methodData(List<String>):
     *  - fileName: file name of executing method,
     *  - className: class name of executing method,
     *  - methodName: name of executing method.
     * @param text(String): message to user.
     * @param isException(Boolean): True - if it is message about an exception, otherwise - False.
     */
    static def logIt(ArrayList methodData, String text, Boolean isException = false){

        String message =
                """File: ${methodData[0]}, Class: ${methodData[1]}, Method: ${methodData[2]}.
                   |RESULT: ${text}""".toString().stripMargin()

        if(isException){
            throw new IOException(message)
        }else{
            println(message)
        }
    }

    /**
     * Get info about executing method.
     * @return List<String>:
     *  - fileName: file name of executing method,
     *  - className: class name of executing method,
     *  - methodName: name of executing method.
     */
    static def getCurrentMethodData(){
        def marker = new Throwable()
        def markerData = StackTraceUtils.sanitize(marker).stackTrace[7]
        return [markerData.fileName, markerData.className, markerData.methodName]
    }
}
//endregion
