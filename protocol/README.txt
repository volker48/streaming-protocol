Instructions:

Server:
To start the server use:
 
$java -jar server.jar path-to-audio-file 

where path-to-audio-file is the absolute path to the audio file to stream.

Client:
To start the client use: 
$java -jar client.jar 
this will start the client in auto discovery mode. 
If the server is on a remote server use:

$java -jar client.jar ip

where ip is either the hostname or physical ip (e.g 192.168.0.1) of the remote server.

Client Controls:
p will pause/unpause the stream
+ will increase the stream rate
- will decrease the stream rate
d will disconnect the client


Path to executables:

The executables are hosted on tux.cs.drexel.edu at /home/mrm99/cs544. Please note that the client plays
streaming audio from the server so it is highly reccommended that the client not be run on tux since you will
not really see anything happening.
