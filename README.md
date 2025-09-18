# IDS Model checker Plugins for Opensource BIMserver

TODO - Add description

## Installation
TODO:
- BIMvie.ws

- Directly from source code:  
Replace `<path/to/plugin/dir>` and `<path/to/home/dir>` with the actual paths on your system. Make sure to execute the command from within an exploded BIMserver JAR folder.

```bash
java -Dorg.apache.cxf.Logger=org.apache.csf.common.logging.Slf4jLogger  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -cp .;lib/* org.bimserver.LocalDevBimServerStarterJar -plugins <path/to/plugin/dir> -home <path/to/home/dir>
```

## Debug
TODO - Intellij
After started BIMserver with the above command, attach to the process using the following settings:
- _Run / Attach to Process_ 
- Select port to 5005
