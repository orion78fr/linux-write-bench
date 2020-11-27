all : bench parser

bench : bench.c
	gcc $^ -o $@ -Wall

parser :
	mkdir -p Parser/bin
	javac Parser/src/fr/upmc/stage/parser/*.java -d Parser/bin

clean :
	rm -rf bench Parser/bin
