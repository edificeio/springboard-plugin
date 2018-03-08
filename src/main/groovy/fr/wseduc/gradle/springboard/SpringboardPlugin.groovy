package fr.wseduc.gradle.springboard

import groovy.io.FileType
import java.io.*;
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringboardPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.task("generateConf") << {
			FileUtils.createFile("conf.properties", "ent-core.json.template", "ent-core.json")
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
			into "static/help/"
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
		project.file("mods")?.mkdirs()
		project.file("sample-be1d/EcoleprimaireEmileZola")?.mkdirs()
		project.file("neo4j-conf")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/scenarios")?.mkdirs()
		project.file("src/test/scala/org/entcore/test/simulations")?.mkdir()

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

		File neo4jConf = project.file("neo4j-conf/neo4j.conf")
		InputStream neo4jConfStream = this.getClass().getClassLoader()
				.getResourceAsStream("neo4j-conf/neo4j.conf")
		FileUtils.copy(neo4jConfStream, neo4jConf)

		File dockerCompose = project.file("docker-compose.yml")
		InputStream dockerComposeStream = this.getClass().getClassLoader()
				.getResourceAsStream("docker-compose.yml")
		FileUtils.copy(dockerComposeStream, dockerCompose)

		File gulpfile = project.file("gulpfile.js")
		InputStream gulpfileStream = this.getClass().getClassLoader()
				.getResourceAsStream("gulpfile.js")
		FileUtils.copy(gulpfileStream, gulpfile)

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

}