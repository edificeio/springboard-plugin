package fr.wseduc.gradle.springboard

import groovy.text.SimpleTemplateEngine

class FileUtils {

	static def createFile(String propertiesFile, String templateFileName, String outputFileName) {
		def props = new Properties()
		props.load(new FileInputStream(new File(propertiesFile)))
		def bindings = [:]
		props.propertyNames().each{prop->
			bindings[prop]=props.getProperty(prop)
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

//	static def copyFolder((Path source, Path target)) {
//		// TODO Auto-generated method stub
//		//java nio folder copy
//		EnumSet options = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
//		//check first if source is a directory
//		if(Files.isDirectory(source)){
//			System.out.println("source is a directory");
//
//			walkFileTree(source, options, Integer.MAX_VALUE, new FileVisitor() {
//
//				@Override
//				public FileVisitResult postVisitDirectory(Path dir,
//														  IOException exc) throws IOException {
//					// TODO Auto-generated method stub
//					//System.out.println(source);
//					return FileVisitResult.CONTINUE;
//				}
//
//				@Override
//				public FileVisitResult preVisitDirectory(Path dir,
//														 BasicFileAttributes attrs)  {
//					// TODO Auto-generated method stub
//					CopyOption[] opt = new CopyOption[]{COPY_ATTRIBUTES,REPLACE_EXISTING};
//					System.out.println("Source Directory "+dir);
//					Path newDirectory = target.resolve(source.relativize(dir));
//					System.out.println("Target Directory "+newDirectory);
//					try{
//						System.out.println("creating directory tree "+Files.copy(dir, newDirectory,opt));
//					}
//					catch(FileAlreadyExistsException x){
//					}
//					catch(IOException x){
//						return FileVisitResult.SKIP_SUBTREE;
//					}
//
//					return CONTINUE;
//				}
//
//				@Override
//				public FileVisitResult visitFile(Path file,
//												 BasicFileAttributes attrs) throws IOException {
//					// TODO Auto-generated method stub
//					//System.out.println("results");
//					System.out.println("Copying file:"+file);
//					kopya(file, target.resolve(source.relativize(file)));
//					return CONTINUE;
//				}
//
//				@Override
//				public FileVisitResult visitFileFailed(Path file,
//													   IOException exc) throws IOException {
//					// TODO -generated method stub
//					return CONTINUE;
//				}
//			});
//		}
//
//	}
//	public static void kopya(Path source,Path target) throws IOException{
//		CopyOption[] options = new CopyOption[]{REPLACE_EXISTING,COPY_ATTRIBUTES};
//		System.out.println("Copied file "+Files.copy(source, target,options));
//
//	}
}
