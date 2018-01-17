package fr.wseduc.gradle.springboard

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project

class FileUtils {

	static def createFile(String propertiesFile, String templateFileName, String outputFileName) {
		def props = new Properties()
		props.load(new FileInputStream(new File(propertiesFile)))
		def bindings = [:]
		props.propertyNames().each{prop->
			if ("assetsPath".equals(prop) &&  !props.getProperty(prop).startsWith(File.separator)) {
				bindings[prop] = new File(props.getProperty(prop)).absolutePath
			} else {
				bindings[prop] = props.getProperty(prop)
			}
		}
		def defaultProps = new Properties()
		defaultProps.load(new FileInputStream(new File("default.properties")))
		defaultProps.propertyNames().each { prop ->
			if (!bindings.containsKey(prop)) {
				bindings[prop] = defaultProps.getProperty(prop)
			}
		}
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
