# Test Server Controller

This is a simple server which can be used to run other servers for end-to-end tests. A test (running Maestro or other
test framework) can make an http request to testserver-controller/start . The normal flow is as follows:

* A test makes an http request to ```/start```. Test Server Controller finds a free port and runs the command specified
  in application.yaml.  The command is expected to start a server process. The following environment variables are set:
  *  TESTSERVER_WORKSPACE - a temporary dir under which files can be saved.
  *  TESTSERVER_PORT - the identified free port on which the server should start.
* Once the specified command has been started a json including the allocated port is returned (e.g. such that the test
  can use this information to connect to the server).  
* The test can then proceed to use the server process which started on the specified port
* When done the test calls ```/stop?port=(allocated-port)``` which will kills the server process
