package fr.wseduc.gradle.springboard

import groovy.io.FileType
import java.io.*;
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringboardPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.task("generateConf") << {
			def rootDir = project.getRootDir().getAbsolutePath()
			FileUtils.createFile(
				"${rootDir}/conf.properties",
				"${rootDir}/gradle.properties",
				"${rootDir}/ent-core.json.template",
				"${rootDir}/ent-core.json",
				project.logger)
		}

		project.task("extractDeployments") << {
			extractDeployments(project)
		}

		project.task("extractHelps") << {
			extractHelps(project)
		}

		project.task("extractTranslations") << {
			extractTranslations(project)
		}

		project.task("extractTheme") << {
			extractTheme(project)
		}

		project.task("init") << {
			extractDeployments(project)
			extractHelps(project)
			extractTranslations(project)
			initFiles(project)
		}

		project.task(dependsOn: ['compileTestScala'], "integrationTest") << {
			gatling(project)
		}

	}

	private void gatling(Project project) {
		def classesDir = project.sourceSets.test.output.classesDir.getPath().replace("java", "scala")
		def simulations = new File(classesDir + File.separator + 'org' + File.separator + 'entcore' + File.separator + 'test' + File.separator + 'simulations')

		project.logger.lifecycle(" ---- Executing all Gatling scenarios from: ${simulations} ----")
		simulations.eachFileRecurse { file ->
			if (file.isFile()) {
				//Remove the full path, .class and replace / with a .
				project.logger.debug("Tranformed file ${file} into")
				def gatlingScenarioClass = (file.getPath() - (classesDir + File.separator) - '.class')
						.replace(File.separator, '.')

				project.logger.debug("Tranformed file ${file} into scenario class ${gatlingScenarioClass}")
				System.setProperty("gatling.http.connectionTimeout", "300000")
				project.javaexec {
					main = 'io.gatling.app.Gatling'
					classpath = project.sourceSets.test.output + project.sourceSets.test.runtimeClasspath
					args '-bf',
							classesDir,
							'-s',
							gatlingScenarioClass,
							'-rf',
							'build/reports/gatling'
				}
			}
		}
		project.logger.lifecycle(" ---- Done executing all Gatling scenarios ----")
	}

	private void extractTheme(Project project){
		project.copy {
			from "deployments/assets/themes"
			into "assets/themes"
		}
	}

	private void extractDeployments(Project project) {
		if (!project.file("deployments")?.exists()) {
			project.file("deployments").mkdir()
		}
		project.copy {
			from {
				project.configurations.deployment.collect { project.zipTree(it) }
			}
			into "deployments/"
		}
		project.file("deployments/org")?.deleteDir()
		project.file("deployments/com")?.deleteDir()
		project.file("deployments/fr")?.deleteDir()
		project.file("deployments/errors")?.deleteDir()
		project.file("deployments/META-INF")?.deleteDir()
		project.file("deployments/org")?.deleteDir()
		project.file("deployments/git-hash")?.delete()
	}

	private void extractHelps(Project project) {
		if (!project.file("static/help")?.exists()) {
			project.file("static/help").mkdirs()
		}
		project.copy {
			from {
				project.configurations.help.collect { project.tarTree(it) }
			}
			into "static/"
		}
	}

	private void extractTranslations(Project project) {
		if (!project.file("i18n")?.exists()) {
			project.file("i18n").mkdirs()
		}
		project.copy {
			from {
				project.configurations.i18n.collect { project.tarTree(it) }
			}
			into "i18n"
		}
	}

	def initFiles(Project project) {
		String version = project.getProperties().get("entCoreVersion")
		project.file("mods")?.mkdirs()
		project.file("sample-be1d/EcoleprimaireEmileZola")?.mkdirs()
		project.file("neo4j-conf")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/scenarios")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/simulations")?.mkdir()
		project.file("aaf-duplicates-test")?.mkdir()
		project.file("conf")?.mkdirs()
		
		// Recursively copy all files from resources/conf to project conf directory
		copyResourceDirectory(project, "conf", "conf")

		File scn = project.file("src/test/scala/org/entcore/test/scenarios/IntegrationTestScenario.scala")
		InputStream scnis = this.getClass().getClassLoader()
				.getResourceAsStream("src/test/scala/org/entcore/test/scenarios/IntegrationTestScenario.scala")
		FileUtils.copy(scnis, scn)

		File sim = project.file("src/test/scala/org/entcore/test/simulations/IntegrationTest.scala")
		InputStream simis = this.getClass().getClassLoader()
				.getResourceAsStream("src/test/scala/org/entcore/test/simulations/IntegrationTest.scala")
		FileUtils.copy(simis, sim)

		File i0 = project.file("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-eleves.csv")
		File i1 = project.file("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-enseignants.csv")
		File i2 = project.file("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-responsables.csv")
		InputStream is0 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-eleves.csv")
		InputStream is1 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-enseignants.csv")
		InputStream is2 = this.getClass().getClassLoader()
				.getResourceAsStream("sample-be1d/EcoleprimaireEmileZola/CSVExtraction-responsables.csv")
		FileUtils.copy(is0, i0)
		FileUtils.copy(is1, i1)
		FileUtils.copy(is2, i2)

		File aaf0 = project.file("aaf-duplicates-test/ENT_Complet_Eleve_0000.xml")
		File aaf1 = project.file("aaf-duplicates-test/ENT_Complet_EtabEducNat_0000.xml")
		File aaf2 = project.file("aaf-duplicates-test/ENT_Complet_PersRelEleve_0000.xml")
		File aaf3 = project.file("aaf-duplicates-test/ENT_Complet_PersEducNat_0000.xml")
		File aaf4 = project.file("aaf-duplicates-test/ficAlimMENESR.dtd")
		InputStream aafs0 = this.getClass().getClassLoader()
				.getResourceAsStream("aaf-duplicates-test/ENT_Complet_Eleve_0000.xml")
		InputStream aafs1 = this.getClass().getClassLoader()
				.getResourceAsStream("aaf-duplicates-test/ENT_Complet_EtabEducNat_0000.xml")
		InputStream aafs2 = this.getClass().getClassLoader()
				.getResourceAsStream("aaf-duplicates-test/ENT_Complet_PersRelEleve_0000.xml")
		InputStream aafs3 = this.getClass().getClassLoader()
				.getResourceAsStream("aaf-duplicates-test/ENT_Complet_PersEducNat_0000.xml")
		InputStream aafs4 = this.getClass().getClassLoader()
				.getResourceAsStream("aaf-duplicates-test/ficAlimMENESR.dtd")
		FileUtils.copy(aafs0, aaf0)
		FileUtils.copy(aafs1, aaf1)
		FileUtils.copy(aafs2, aaf2)
		FileUtils.copy(aafs3, aaf3)
		FileUtils.copy(aafs4, aaf4)

		File neo4jConf = project.file("neo4j-conf/neo4j.conf")
		InputStream neo4jConfStream = this.getClass().getClassLoader()
				.getResourceAsStream("neo4j-conf/neo4j.conf")
		FileUtils.copy(neo4jConfStream, neo4jConf)

		File initSql = project.file("docker-entrypoint-initdb.d/init.sql")
		InputStream initSqlStream = this.getClass().getClassLoader()
				.getResourceAsStream("docker-entrypoint-initdb.d/init.sql")
		if(!initSql.exists()) {
			project.mkdir("docker-entrypoint-initdb.d")
			initSql.createNewFile()
		}
		FileUtils.copy(initSqlStream, initSql)

		final String dockerComposeFileName = isM1() ? "docker-compose.mac.yml" : "docker-compose.yml"
		File dockerCompose = project.file("docker-compose.yml")
		InputStream dockerComposeStream = this.getClass().getClassLoader()
				.getResourceAsStream(dockerComposeFileName)
		FileUtils.copy(dockerComposeStream, dockerCompose)

		File packageJson = project.file("package.json")
		InputStream packageJsonStream = this.getClass().getClassLoader()
				.getResourceAsStream("package.json")
		FileUtils.copy(packageJsonStream, packageJson)


		File entcoreJsonTemplate = project.file("ent-core.json.template")
		FileUtils.copy(this.getClass().getClassLoader().getResourceAsStream("ent-core.json.template"),
				entcoreJsonTemplate)

		String filename = "conf.properties"
		File confProperties = project.file(filename)
		Map confMap = FileUtils.createOrAppendProperties(confProperties, filename)

		String filenameDefault = "default.properties"
		File defaultProperties = project.file(filenameDefault)
		Map defaultMap = FileUtils.createOrAppendProperties(defaultProperties, filenameDefault)

		Map appliPort = [:]
		project.file("deployments").eachDir {
			it.eachDir { dir ->
				String dest = "migration".equals(dir.name) ? dir.name + File.separator + it.name : dir.name
				new AntBuilder().copy( todir: dest ) {
					fileset( dir: dir.absolutePath )
				}
			}
			it.eachFile(FileType.FILES) { file ->
				File f
				switch (file.name) {
					case "conf.json.template":
						f = entcoreJsonTemplate
						f.append(",\n")
						f.append(file.text)
						file.eachLine { line ->
							def matcher = line =~ /\s*\t*\s*"port"\s*:\s*([0-9]+)[,]?\s*\t*\s*/
							if (matcher.find()) {
								appliPort.put(it.name, matcher.group(1))
							}
						}
						break;
					case "test.scala":
						f = scn
						f.append("\n" + file.text)
						break;
					case "conf.properties" :
						FileUtils.appendProperties(project, file, confMap)
						break;
					case "default.properties" :
						FileUtils.appendProperties(project, file, defaultMap)
						break;
				}
			}
		}

		InputStream httpProxy = this.getClass().getClassLoader().getResourceAsStream("http-proxy.json.template")
		entcoreJsonTemplate.append(httpProxy.text)
		appliPort.each { k, v ->
			entcoreJsonTemplate.append(
					",\n" +
					"          {\n" +
					"            \"location\": \"/" + k + "\",\n" +
					"            \"proxy_pass\": \"http://localhost:" + v + "\"\n" +
					"          }"
			)
		}
		entcoreJsonTemplate.append(
				"        ]\n" +
				"      }\n" +
				"    }\n" +
				"<% } %>" +
				"  ]\n" +
				"}"
		)
		scn.append("\n}")

		if (!confMap.containsKey("entcoreVersion")) {
			confProperties.append("entcoreVersion=" + version + "\n")
		}
	}

	private static boolean isM1() {
		return "true".equalsIgnoreCase(System.getenv("IS_M1")) ||
				"aarch64".equalsIgnoreCase(System.getProperty("os.arch"))
	}

	/**
	 * Recursively copies all files and directories from a resource directory to a target directory
	 */
	private void copyResourceDirectory(Project project, String resourcePath, String targetPath) {
		// Get the resource URL
		URL resourceUrl = this.getClass().getClassLoader().getResource(resourcePath)
		if (resourceUrl == null) {
			project.logger.warn("Resource directory not found: ${resourcePath}")
			return
		}

		try {
			// Handle different URL protocols (jar: for packaged plugin, file: for development)
			if (resourceUrl.protocol == "jar") {
				// Extract from JAR file
				copyFromJar(project, resourceUrl, resourcePath, targetPath)
			} else {
				// Copy from file system (development mode)
				copyFromFileSystem(project, new File(resourceUrl.toURI()), targetPath)
			}
		} catch (Exception e) {
			project.logger.error("Failed to copy resource directory ${resourcePath}: ${e.message}")
		}
	}

	private void copyFromJar(Project project, URL jarUrl, String resourcePath, String targetPath) {
		// Parse JAR URL to get the JAR file path
		String jarPath = jarUrl.path.substring(5, jarUrl.path.indexOf("!"))
		def jarFile = new java.util.jar.JarFile(jarPath)
		
		try {
			jarFile.entries().each { entry ->
				if (entry.name.startsWith(resourcePath + "/") && !entry.isDirectory()) {
					String relativePath = entry.name.substring(resourcePath.length() + 1)
					File targetFile = project.file("${targetPath}/${relativePath}")
					
					// Create parent directories
					targetFile.parentFile?.mkdirs()
					
					// Copy file content
					InputStream inputStream = jarFile.getInputStream(entry)
					FileUtils.copy(inputStream, targetFile)
					inputStream.close()
				}
			}
		} finally {
			jarFile.close()
		}
	}

	private void copyFromFileSystem(Project project, File sourceDir, String targetPath) {
		if (!sourceDir.exists() || !sourceDir.isDirectory()) {
			return
		}
		
		sourceDir.eachFileRecurse { file ->
			if (file.isFile()) {
				String relativePath = sourceDir.toPath().relativize(file.toPath()).toString()
				File targetFile = project.file("${targetPath}/${relativePath}")
				
				// Create parent directories
				targetFile.parentFile?.mkdirs()
				
				// Copy file
				file.withInputStream { inputStream ->
					FileUtils.copy(inputStream, targetFile)
				}
			}
		}
	}

}