package fr.wseduc.gradle.springboard

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project

class FileUtils {

	static def createFile(String propertiesFile, 
						  String gradleFile,
						  String templateFileName,
						  String outputFileName,
						  Map additionalBindings = [:]) {
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
		// Add any additional bindings
		bindings.putAll(additionalBindings)
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
