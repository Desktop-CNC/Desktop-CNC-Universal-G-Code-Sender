CXX = g++
LDFLAGS = -llgpio -pthread

all: atc_servos

atc_servos: atc_servos.o
	$(CXX) atc_servos.o -o atc_servos $(LDFLAGS)
atc_servos.o: atc_servos.cpp
	$(CXX) -c atc_servos.cpp
clean:
	rm -f *.o atc_servos
