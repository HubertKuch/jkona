# Kona

![Kona Logo](https://via.placeholder.com/150)

**Kona** is a lightweight framework for building cross-platform desktop applications using Java and web technologies. It provides a simple and intuitive API for creating and managing windows, handling messages between the frontend and backend, and packaging your application for distribution.

## Features

- **Cross-Platform**: Write your application once and run it on multiple operating systems.
- **Java + Web**: Combine the power of Java for the backend with the flexibility of web technologies for the frontend.
- **Simple API**: A clean and modern API that makes it easy to get started.
- **Automatic Environment Detection**: Automatically detects whether you are in a development or production environment.
- **Extensible**: Easily extend the framework with custom implementations for different platforms or web view engines.

## Supported Platforms

| Operating System | Architecture | Supported |
| ---------------- | ------------ | :-------: |
| Linux            | x86_64       |    ✅     |
| Windows          | x86_64       |    TBD    |
| macOS            | x86_64       |    TBD    |
| macOS            | aarch64      |    TBD    |

## Prerequisites

- **Java 21+**: Kona requires Java 21 or higher.
- **GTK 3 and WebKit2GTK**: For Linux, you need to have GTK 3 and WebKit2GTK installed.

  ```bash
  # Debian/Ubuntu
  sudo apt-get install libgtk-3-dev libwebkit2gtk-4.0-dev

  # Fedora/CentOS
  sudo dnf install gtk3-devel webkit2gtk4.0-devel
  ```

## Getting Started

1.  **Create a new Java project**: Start by creating a new Java project with your favorite build tool.

2.  **Add the Kona dependency**: Add the Kona dependency to your `build.gradle` or `pom.xml` file.

    ```groovy
    // build.gradle
    repositories {
        mavenCentral()
    }

    dependencies {
        implementation 'io.github.hubertkuch:kona:0.1.0' // Replace with the latest version
    }
    ```

3.  **Create your main application class**:

    ```java
    import io.github.hubertkuch.kona.Kona;

    public class Main {
        public static void main(String[] args) {
            new Kona.Builder()
                    .title("My Kona App")
                    .build()
                    .run();
        }
    }
    ```

4.  **Create your frontend**: Create a new frontend project using your favorite web framework (e.g., React, Vue, Svelte). Install the `kona-js` library:

    ```bash
    npm install kona-js
    ```

5.  **Run your application**: Run your Java application. It will automatically detect that you are in a development environment and load the content from the Vite dev server at `http://localhost:5173`.

## `kona-js` Library

The `kona-js` library provides a simple way to communicate with the Java backend from your frontend.

### `kona.call(controller, action, payload)`

The `kona.call` function sends a message to the backend and returns a promise that resolves with the response.

- `controller` (string): The name of the controller to handle the message.
- `action` (string): The name of the action to be performed.
- `payload` (object): The data to be sent to the backend.

**Example:**

```javascript
import kona from "kona-js";

async function handleClick() {
    const response = await kona.call("test", "test", {
        message: "Hello from React!"
    });
    console.log(response); // { response: "Hello from Java! ..." }
}
```

## Configuration

You can configure your Kona application using the `Kona.Builder` class:

```java
new Kona.Builder()
        .title("My Custom App")
        .width(1024)
        .height(768)
        .controllerPackage("com.example.controllers")
        .initialUri("http://localhost:8080")
        .build()
        .run();
```

## Building for Production with GraalVM

The primary goal of Kona is to create a standalone, native binary of your application using GraalVM. This provides a lightweight, fast-starting application with no need for a separate JVM installation.

1.  **Install GraalVM**: Follow the official instructions to [install GraalVM](https://www.graalvm.org/docs/getting-started/) and the `native-image` tool.

    ```bash
    sdk install java 21.0.1-graal
    gu install native-image
    ```

2.  **Build your frontend**: Build your frontend project. This will typically generate a `dist` directory with your static assets.

3.  **Copy the frontend assets**: Copy the contents of the `dist` directory to the `src/main/resources/webapp` directory in your Java project. These resources will be embedded into the native binary.

4.  **Configure your project for GraalVM**: In your project's `build.gradle` file, add the GraalVM plugin and configure it to build a native binary. Kona is a library, so it does not bundle the GraalVM plugin.

    ```groovy
    plugins {
        id 'org.graalvm.buildtools.native' version '0.9.28'
    }

    graalvmNative {
        binaries {
            main {
                mainClass = 'com.example.Main' // Replace with your main class
                buildArgs.add('--enable-preview')
            }
        }
    }
    ```

5.  **Build the native binary**: Run the `nativeCompile` task to build the native binary.

    ```bash
    ./gradlew nativeCompile
    ```

    The native executable will be created in the `build/native/nativeCompile` directory.

## License

Kona is licensed under the [MIT License](LICENSE).
