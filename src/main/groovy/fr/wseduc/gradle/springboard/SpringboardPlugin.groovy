package fr.wseduc.gradle.springboard

import groovy.io.FileType
import java.io.*;
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringboardPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.task("generateConf") << {
			def rootDir = project.getRootDir().getAbsolutePath()
			FileUtils.createFile("${rootDir}/conf.properties", "${rootDir}/gradle.properties", "${rootDir}/ent-core.json.template", "${rootDir}/ent-core.json")
			
			// Generate docker-compose.yml with variabilized version and M1 detection
			Map additionalBindings = [isM1: isM1() ? "true" : "false", vertxCliVersion: "latest"]
			FileUtils.createFile("${rootDir}/conf.properties", "${rootDir}/gradle.properties", "${rootDir}/docker-compose.yml.template", "${rootDir}/docker-compose.yml", additionalBindings)
			
			extractMods(project)
		}

		project.task("extractMods") << {
			extractMods(project)
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

		// Setup JS tests tasks
		setupJsTestsTasks(project)

	}

	/**
	 * Sets up the JS tests tasks: downloadTestsJS, unzip*Jar tasks, and prepareJsTests
	 * This allows springboards to use these tasks without having to redefine them
	 * Automatically detects all dependencies with classifier "testJs" or "tests" from all configurations
	 */
	private void setupJsTestsTasks(Project project) {
		// Supported classifiers for test JS artifacts
		def testClassifiers = ['testJs', 'tests']

		// Create a single configuration to hold all testJs dependencies (non-transitive)
		if (!project.configurations.findByName('testJsJars')) {
			project.configurations.create('testJsJars') {
				transitive = false
			}
		}

		// Create entcoreTestJsJar configuration for backward compatibility (non-transitive)
		if (!project.configurations.findByName('entcoreTestJsJar')) {
			project.configurations.create('entcoreTestJsJar') {
				transitive = false
			}
		}

		// Use afterEvaluate to ensure all dependencies are declared before we scan them
		project.afterEvaluate {
			// Collect all testJs/tests dependencies from all configurations
			def testJsDeps = []
			
			project.configurations.each { config ->
				config.dependencies.each { dep ->
					if (dep instanceof org.gradle.api.artifacts.ExternalModuleDependency) {
						dep.artifacts.each { artifact ->
							if (artifact.classifier in testClassifiers) {
								testJsDeps << [
									group: dep.group,
									name: dep.name,
									version: dep.version,
									classifier: artifact.classifier, // Keep track of the actual classifier
									module: dep.name // Use artifact name as module identifier
								]
							}
						}
					}
				}
			}

			// Also check for configurations named *TestJsJar (legacy support)
			project.configurations.findAll { it.name.endsWith('TestJsJar') }.each { config ->
				config.dependencies.each { dep ->
					if (!testJsDeps.find { it.group == dep.group && it.name == dep.name }) {
						// For legacy configs, try to detect the classifier from artifact or default to 'testJs'
						def classifier = 'testJs'
						if (dep instanceof org.gradle.api.artifacts.ExternalModuleDependency && dep.artifacts) {
							def art = dep.artifacts.find { it.classifier in testClassifiers }
							if (art) classifier = art.classifier
						}
						testJsDeps << [
							group: dep.group,
							name: dep.name,
							version: dep.version,
							classifier: classifier,
							module: dep.name
						]
					}
				}
			}

			// Remove duplicates based on group:name
			testJsDeps = testJsDeps.unique { "${it.group}:${it.name}" }

			if (testJsDeps.isEmpty()) {
				project.logger.lifecycle("[prepareJsTests] No testJs dependencies found - nothing to do")
				return
			}

			project.logger.lifecycle("[prepareJsTests] Found ${testJsDeps.size()} testJs module(s) to prepare:")
			testJsDeps.each { dep ->
				project.logger.lifecycle("[prepareJsTests]   - ${dep.group}:${dep.name}:${dep.version}:${dep.classifier}")
			}

			// Add all testJs dependencies to our configuration with their actual classifier
			testJsDeps.each { dep ->
				project.dependencies.add('testJsJars', "${dep.group}:${dep.name}:${dep.version}:${dep.classifier}")
			}

			// Configure downloadTestsJS task
			def downloadTask = project.tasks.findByName('downloadTestsJS')
			if (downloadTask) {
				downloadTask.from project.configurations.testJsJars
				downloadTask.doLast {
					project.logger.lifecycle("[downloadTestsJS] Downloaded ${testJsDeps.size()} testJs JAR(s) to ${project.buildDir}/libs/testJs")
				}
			}

			// Create unzip tasks for each module
			testJsDeps.each { dep ->
				def taskName = "unzip${dep.module.capitalize()}TestJsJar"
				
				if (!project.tasks.findByName(taskName)) {
					project.task(taskName, type: org.gradle.api.tasks.Copy, dependsOn: 'downloadTestsJS') {
						from {
							project.configurations.testJsJars.filter { file ->
								file.name.contains(dep.name)
							}.collect { project.zipTree(it) }
						}
						into "${project.buildDir}/libs/testJs/${dep.module}"
						doLast {
							project.logger.lifecycle("[${taskName}] Extracted ${dep.group}:${dep.name}:${dep.version} to ${project.buildDir}/libs/testJs/${dep.module}")
						}
					}
				}
			}

			// Update prepareJsTests dependencies
			def prepareTask = project.tasks.findByName('prepareJsTests')
			if (prepareTask) {
				testJsDeps.each { dep ->
					def taskName = "unzip${dep.module.capitalize()}TestJsJar"
					if (project.tasks.findByName(taskName)) {
						prepareTask.dependsOn taskName
					}
				}
				prepareTask.doLast {
					project.logger.lifecycle("[prepareJsTests] Successfully prepared ${testJsDeps.size()} testJs module(s)")
				}
			}
		}

		// Create downloadTestsJS task (will be configured in afterEvaluate)
		project.task('downloadTestsJS', type: org.gradle.api.tasks.Copy) {
			into "${project.buildDir}/libs/testJs"
		}

		// Create aggregate prepareJsTests task (dependencies will be added in afterEvaluate)
		project.task('prepareJsTests') {
			description = 'Prepares all JS test dependencies'
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

	private void extractMods(Project project) {
		if (!project.file("mods")?.exists()) {
			project.file("mods").mkdir()
		}
		
		// Define default mods that should always be included
		// Format: [groupId~artifactId, versionProperty, githubRepoPath]
		def defaultModsSpecs = [
			["io.vertx~mod-mongo-persistor", "modMongoPersistorVersion", "edificeio/mod-mongo-persistor"],
			["fr.wseduc~mod-zip", "modZipVersion", "edificeio/mod-zip"],
			["fr.wseduc~mod-postgresql", "modPostgresVersion", "edificeio/mod-postgresql"],
			["com.opendigitaleducation~mod-json-schema-validator", "modJsonschemavalidatorVersion", "edificeio/mod-json-schema-validator"],
			["fr.cgi~mod-sftp", "modSftpVersion", "OPEN-ENT-NG/mod-sftp", "dev"],
			["fr.wseduc~mod-webdav", "modWebdavVersion", "OPEN-ENT-NG/mod-webdav", "master"],
			["fr.wseduc~mod-sms-proxy", "modSmsproxyVersion", "edificeio/mod-sms-sender"],
			["fr.wseduc~mod-pdf-generator", "modPdfgenerator", "edificeio/mod-pdf-generator"],
			["org.entcore~infra", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~app-registry", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~session", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~auth", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~directory", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~workspace", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~communication", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~portal", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~conversation", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~feeder", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~timeline", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~broker", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~cas", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~archive", "entCoreVersion", "edificeio/entcore"],
			["org.entcore~admin", "entCoreVersion", "edificeio/entcore"]
		]

		def defaultBranch = project.hasProperty("modsDefaultBranch") ? project.property("modsDefaultBranch") : "dev"
		
		// Parse default mods and resolve version properties
		def defaultMods = defaultModsSpecs.collect { spec ->
			def groupArtifact = spec[0].split('~')
			def versionProperty = spec[1]
			
			def version = project.hasProperty(versionProperty) ? project.property(versionProperty) : null
			if (version == null && spec.size() >= 3) {
				def githubRepoPath = spec[2]
				def branch = defaultBranch
				if (spec.size() >= 4) {
					branch = spec[3]
				}
				version = fetchVersionFromGitHub(project, branch, githubRepoPath)
			}
			if (version == null) {
				project.logger.warn("Version property ${versionProperty} not found and could not fetch from GitHub for ${groupArtifact[0]}:${groupArtifact[1]}")
				return null
			}
			[group: groupArtifact[0], name: groupArtifact[1], version: version]
		}.findAll { it != null }
		
		// Get all deployment dependencies
		def deploymentDeps = project.configurations.deployment.dependencies.collect { 
			[group: it.group, name: it.name, version: it.version]
		}
		
		// Combine default mods and deployment dependencies (avoid duplicates)
		def allDeps = (defaultMods + deploymentDeps).unique { "${it.group}:${it.name}" }
		
		// Use thread pool for parallel processing
		def threadPool = Executors.newFixedThreadPool(Math.min(Runtime.runtime.availableProcessors(), allDeps.size()))
		
		// Track first failure for fail-fast behavior
		def firstFailure = null
		
		try {
			// Download and process all fat jars in parallel
			allDeps.each { dep ->
				threadPool.submit({
					// Check if another task has already failed
					if (firstFailure != null) {
						return
					}
					
					try {
						// Create a detached configuration for this specific fat jar
						def fatDep = project.dependencies.create("${dep.group}:${dep.name}:${dep.version}:fat")
						def fatConfig = project.configurations.detachedConfiguration(fatDep)
						fatConfig.transitive = false
						
						// Get the fat jar file
						def fatJarFile = fatConfig.singleFile
						
						// Create the new filename: groupId~artifactId~version-fat.jar
						def newFileName = "${dep.group}~${dep.name}~${dep.version}-fat.jar"
						def targetFile = project.file("mods/${newFileName}")
						
						// Copy and rename the fat jar
						synchronized(project) {
							project.copy {
								from fatJarFile
								into "mods/"
								rename { newFileName }
							}
						}
						
						// Unzip the fat jar
						synchronized(project) {
							project.copy {
								from project.zipTree(targetFile)
								into "mods/${dep.group}~${dep.name}~${dep.version}"
							}
						}
						
						project.logger.info("Successfully processed fat jar for ${dep.group}:${dep.name}:${dep.version}")
					} catch (Exception e) {
						def errorMsg = "Could not download fat jar for ${dep.group}:${dep.name}:${dep.version}: ${e.message}"
						project.logger.error(errorMsg)
						
						// Store first failure and immediately shutdown thread pool
						synchronized(this) {
							if (firstFailure == null) {
								firstFailure = new RuntimeException(errorMsg, e)
								threadPool.shutdownNow()
							}
						}
					}
				})
			}
		} finally {
			threadPool.shutdown()
			threadPool.awaitTermination(30, TimeUnit.MINUTES)
		}
		
		// Throw the first failure if any occurred
		if (firstFailure != null) {
			throw firstFailure
		}
	}

	/**
	 * Fetches the version from GitHub pom.xml for a given module
	 * @param project The Gradle project
	 * @param githubRepoPath The GitHub repository path (e.g., "edificeio/entcore" or full URL)
	 */
	private String fetchVersionFromGitHub(Project project, String defaultBranch, String githubRepoPath) {
		try {
			// Skip if placeholder is not replaced
			if (githubRepoPath.isEmpty()) {
				project.logger.debug("GitHub repository path not configured, skipping version fetch")
				return null
			}
			
			// Construct the raw GitHub URL
			def githubUrl
			if (githubRepoPath.startsWith("http")) {
				// Full URL provided
				githubUrl = githubRepoPath
			} else {
				// Repository path provided (e.g., "edificeio/entcore")
				githubUrl = "https://raw.githubusercontent.com/${githubRepoPath}/${defaultBranch}/pom.xml"
			}
			
			project.logger.info("Fetching version from GitHub: ${githubUrl}")
			
			def url = new URL(githubUrl)
			def connection = url.openConnection()
			connection.setConnectTimeout(10000)
			connection.setReadTimeout(10000)
			
			def pomContent = connection.inputStream.text
			
			// Parse pom.xml to extract version
			def pomXml = new XmlSlurper().parseText(pomContent)
			def version = pomXml.version?.text()
			
			if (version) {
				project.logger.info("Successfully fetched version ${version} from GitHub for ${githubRepoPath}")
				return version
			} else {
				project.logger.warn("No version found in pom.xml for ${githubRepoPath}")
				return null
			}
		} catch (Exception e) {
			project.logger.warn("Failed to fetch version from GitHub for ${githubRepoPath}: ${e.message}")
			return null
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

		File dockerCompose = project.file("docker-compose.yml.template")
		InputStream dockerComposeStream = this.getClass().getClassLoader()
				.getResourceAsStream("docker-compose.yml")
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