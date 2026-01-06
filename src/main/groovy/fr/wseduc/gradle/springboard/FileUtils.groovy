package fr.wseduc.gradle.springboard

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project

class FileUtils {
	
	/**
	 * Map of module property names to module names on GitHub
	 * so we can fetch their version if not specified in gradle.properties.
	 */
	static final Map<String, String> MOD_NAME_MAPPINGS = [
		modPdfgenerator: 'mod-pdf-generator',
		modMongoPersistorVersion: 'mod-mongo-persistor',
		modImageResizerVersion: 'mod-image-resizer',
		modZipVersion: 'mod-zip',
		modPostgresVersion: 'mod-postgresql',
		modJsonschemavalidatorVersion: 'mod-json-schema-validator',
		modSftpVersion: 'OPEN-ENT-NG/mod-sftp',
		modWebdavVersion: 'OPEN-ENT-NG/mod-webdav',
		modSmsproxyVersion: 'mod-sms-sender'
	]

	static def createFile(String propertiesFile, 
						  String gradleFile,
						  String templateFileName,
						  String outputFileName,
						  logger) {
		def props = new Properties()
		def file = new File(propertiesFile)
		def rootDirectory = file.getParentFile()
		props.load(new FileInputStream(file))
		def bindings = [:]
		props.propertyNames().each{prop->
			if ("assetsPath".equals(prop) &&  !props.getProperty(prop).startsWith(File.separator)) {
				bindings[prop] = new File(props.getProperty(prop)).absolutePath
			} else {
				bindings[prop] = props.getProperty(prop)
			}
		}
		def gradleProps = new Properties()
		gradleProps.load(new FileInputStream(gradleFile))
		removeUselessGradleProps(gradleProps);
		def defaultProps = new Properties()
		defaultProps.load(new FileInputStream(new File(rootDirectory, "default.properties")))
		defaultProps.putAll(gradleProps)
		defaultProps.propertyNames().each { prop ->
			if (!bindings.containsKey(prop)) {
				bindings[prop] = defaultProps.getProperty(prop)
			}
		}
		populateModuleVersionsNotInBindings(bindings, logger)
		def engine = new SimpleTemplateEngine()
		def templateFile = new File(templateFileName)
		def output = engine.createTemplate(templateFile).make(bindings)
		def outputFile = new File(outputFileName)
		def parentFile = outputFile.getParentFile()
		if (parentFile != null)	parentFile.mkdirs()
		def fileWriter = new FileWriter(outputFile)
		fileWriter.write(output.toString())
		fileWriter.close()
	}

	/**
	 * For each module defined in MOD_NAME_MAPPINGS, if the corresponding
	 * property is not defined in the bindings map, fetch the latest release
	 * version from GitHub and add it to the bindings.
	 */
	static def populateModuleVersionsNotInBindings(Map bindings, logger) {
		String modsDefaultBranch = System.getenv("MODS_DEFAULT_BRANCH")
		MOD_NAME_MAPPINGS.each { key, modName ->
			if (!bindings.containsKey(key)) {
				logger.lifecycle("Fetching latest release version for module: " + modName)	
				// Fetch raw pom.xml from GitHub for the module. It will successively try
				// the default branch (if defined in env MODS_DEFAULT_BRANCH), then master,
				// then main.
				// If it still does not exist raise an error
				String[] branches = []
				if (modsDefaultBranch != null && !modsDefaultBranch.isEmpty()) {
					branches = [modsDefaultBranch, 'master', 'main']
				} else {
					branches = ['master', 'main']
				}
				String latestVersion = null
				for (String branch : branches) {
					try {
						String modUrl;
						if (modName.contains('/')) {
							modUrl = modName
						} else {
							modUrl = "edificeio/" + modName
						}
						URL pomUrl = new URL("https://raw.githubusercontent.com/" + modUrl + "/refs/heads/" + branch + "/pom.xml")
						InputStream pomStream = pomUrl.openStream()
						String pomContent = pomStream.getText("UTF-8")
						// Parse the xml and extract the version of the artifact
						def pomXml = new XmlSlurper().parseText(pomContent)
						latestVersion = pomXml.version.text()
						break
					} catch (Exception e) {
						// Continue to next branch
					}
				}
				if (latestVersion == null) {
					throw new RuntimeException("Could not find the latest version of module: " + modName)
				}
				logger.lifecycle("Using latest version for module " + modName + " : " + latestVersion)
				bindings[key] = latestVersion
			}
		}
	}

	/**
	* Removes from the specified props the properties that are not
	* useful for the generation of the entcore.json
	*/
	static def removeUselessGradleProps(Properties props) {
		props.remove("modowner");
		props.remove("modname");
		props.remove("version");
		props.remove("produceJar");
	}

	static def copy(InputStream is, File output) {
		int read;
		byte[] bytes = new byte[1024];
		FileOutputStream out = new FileOutputStream(output)
		while ((read = is.read(bytes)) != -1) {
			out.write(bytes, 0, read);
		}
	}

	static def createOrAppendProperties(File confProperties, String filename) {
		Boolean confExists = confProperties.exists()
		Map confPropertiesMap = [:]
		if (!confExists) {
			copy(FileUtils.class.getClassLoader().getResourceAsStream(filename),
					confProperties)
			confProperties.append("\n")
		} else {
			confProperties.eachLine {
				if (!it.isEmpty()) {
					String[] l = it.split("=", 2)
					confPropertiesMap.put(l[0], l[1])
				}
			}
			FileUtils.class.getClassLoader().getResourceAsStream(filename).eachLine {
				if (!it.isEmpty()) {
					String[] l = it.split("=", 2)
					if (!confPropertiesMap.containsKey(l[0])) {
						confProperties.append(it + "\n")
					}
					confPropertiesMap.put(l[0], l[1])
				}
			}
		}
		return confPropertiesMap
	}

	static def appendProperties(Project project, File file, confMap) {
		File f
		f = project.file(file.name)
		f.eachLine {
			String[] l = it.split("=", 2)
			if (!confMap.containsKey(l[0])) {
				f.append(it + "\n")
			}
			confMap.put(l[0], l[1])
		}
	}

}
