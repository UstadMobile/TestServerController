# Test Server Controller

This is a simple server which can be used to start/stop other servers for end-to-end tests. A test (running Maestro or
other test framework) can make http requests to testserver-controller/start and testserver-controller/stop to control 
servers. Multiple server processes can run concurrently.

The normal flow is:
* A test makes an http request to ```/start```. Test Server Controller finds a free port and runs the command specified
  in (as per ```testservercontroller.cmd```).  The command is expected to start a server process. The command receives
  environment variables that specify the port to use and a temporary directory for files.
* Once the specified command has been started a json including the allocated port is returned (e.g. such that the test
  can use this information to connect to the server).
* The test can then proceed to use the server process which started on the specified port
* When done the test calls ```/stop?port=(allocated-port)``` which will call the shutdown url specified by 
  ```testservercontroller.shutdown.url``` and kill the process.

## Available configuration options

These are [KTOR configuration options](https://ktor.io/docs/server-configuration-file.html#custom-property) and can be
specified in the application.yaml config file or on the command line (using -P:propName=value).

* *testservercontroller.cmd* the command to run to start a server. This command can use environment variables as below.
* *testservercontroller.basedir* the base directory to use for workspaces. 
* *testservercontroller.shutdown.url* the URL to shutdown a server that has been started by running 
  ```testservercontroller.cmd``` e.g. as implemented using [KTOR shutdown url](https://ktor.io/docs/server-shutdown-url.html).
* __testservercontroller.env.*__ an environment variable to pass through when running ```testservercontroller.cmd```
  e.g. if ```-P:testservercontroller.env.MYVAR=value``` then the environment variable MYVAR will be available to 
  the command specified by ```testservercontroller.cmd``` .
* *testservercontroller.portRange* when /start is called test server controller will look for a free port. The port range
  can be specified (e.g. to ensure it is within a range allowed by a firewall etc). e.g. ```-P:testservercontroller.portRange=8000-8010```
* *testservercontroller.urlsubstitution* when specified this will replace the automatically generated URL for a started
  server (which uses the host header from /start request). This allows for the use of a reverse proxy etc (e.g. to run
  tests over https). ```_PORT_``` will be replaced with the allocated port.
* *ktor.deployment.port* the port that the test server controller itself will run on
* *ktor.deployment.shutdown.url* the URL to shut the server down.

## Environment variables

Accessible to the command specified by ```testservercontroller.cmd```.
*  TESTSERVER_WORKSPACE - a temporary dir under which files can be saved.
*  TESTSERVER_PORT - the identified free port on which the server should start.
*  URL_SUBSTITUTION (optional): if specified, the url returned in JSON from /start and used to wait for the server to
   be ready will be replaced. This can be useful to use a reverse proxy e.g. for testing over https. _PORT_ will be
   replaced with the
*  Custom environment variables for the command can be set using testservercontroller.env.(varname) in application
   config or on the command line e.g. ```-P:testservercontroller.env.MYVAR=value```.
