# compiler

## Run it

Java 21 is required. On macOS, select it if a newer JDK is currently the default:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Then run a source file with:

```sh
./gradlew runParser -Pargs="examples/test1.txt"
```

The lexer-only demonstration is run with:

```sh
./gradlew run --args="examples/test1.txt"
```

Run the automated tests with:

```sh
./gradlew test
```

Invalid programs report source-positioned syntax or semantic errors and stop
before intermediate code is generated.
