# Must match the build toolchain (Java 21); the jar is compiled with --enable-preview, whose
# class files are locked to their exact JDK major version and require --enable-preview at runtime.
FROM azul/zulu-openjdk:21-latest
COPY build/libs/supplies-database.jar /supplies-database.jar
CMD ["java","--enable-preview","-jar","supplies-database.jar"]
