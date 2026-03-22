CXX = g++
LDFLAGS = -llgpio -pthread

all: pi_main

pi_main: main.o
	$(CXX) main.o -o pi_main $(LDFLAGS)
main.o: main.cpp
	$(CXX) -c main.cpp
clean:
	rm -f *.o pi_main
