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
			FileUtils.createFile("${rootDir}/conf.properties", "${rootDir}/ent-core.json.template", "${rootDir}/ent-core.json")
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

		final String dockerComposeFileName = isM1() ? "docker-compose.mac.yml" : "docker-compose.yml"
		File dockerCompose = project.file("docker-compose.yml")
		InputStream dockerComposeStream = this.getClass().getClassLoader()
				.getResourceAsStream(dockerComposeFileName)
		FileUtils.copy(dockerComposeStream, dockerCompose)

		File gulpfile = project.file("gulpfile.js")
		InputStream gulpfileStream = this.getClass().getClassLoader()
				.getResourceAsStream("gulpfile.js")
		FileUtils.copy(gulpfileStream, gulpfile)

		File packageJson = project.file("package.json")
		InputStream packageJsonStream = this.getClass().getClassLoader()
				.getResourceAsStream("package.json")
		FileUtils.copy(packageJsonStream, packageJson)


		String filename = "conf.properties"
		File confProperties = project.file(filename)
		Map confMap = FileUtils.createOrAppendProperties(confProperties, filename)

		String filenameDefault = "default.properties"
		File defaultProperties = project.file(filenameDefault)
		Map defaultMap = FileUtils.createOrAppendProperties(defaultProperties, filenameDefault)

		File dotEnvFile = project.file(".env")
		if(!dotEnvFile.exists()) {
			String homeDir = "~"
			dotEnvFile.withWriter {writer -> writer.write("SSH_DIR=${homeDir}/.ssh\nVAULT_TOKEN_PATH=${homeDir}/.vault-token")}
		}

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

}