#include <iostream>
#include <lgpio.h>
#include <unistd.h>
#include <cmath>

class ServoSg90 {
    private:
    // servo library parameters 
    const uint CHIP = 4;
    const int HANDLER;
    const int FREQUENCY;
    const uint PIN;
    // feed-forward parameters 
    double current_position = -90;
    const int MIN_MILLIS = 10; // minimum time to wait for servo to move 

    /**
     * @brief Initializes the `ServoSg90` instance upon instantiation.
     */
    void initialize() {
        // ensure gpiochip is functional
        if (HANDLER < 0) {
            std::cerr << "Could not open gpiochip 4." << std::endl;
            exit(1);
        }
        // ensure servo has claimed its signal pin
        if (lgGpioClaimOutput(HANDLER, 0, PIN, 0) < 0) {
            std::cerr << "Could not claim BCM pin. It might be in use." << std::endl;
            lgGpiochipClose(HANDLER);
            exit(1);
        }
        setPosition(current_position);
    }

    public:

    /**
     * @brief Creates a `ServoSg90` instance. 
     * @param bcm_pin (uint) : The specified Raspberry Pi BCM Pin
     * @param freq (uint) : The specified servo motor frequency in Hertz
     */
    ServoSg90(uint bcm_pin, uint freq) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(freq), PIN(bcm_pin) {
        initialize();
    }

    /**
     * @brief Creates a `ServoSg90` instance. 
     * @param bcm_pin (uint) : The specified Raspberry Pi BCM Pin
     */
    ServoSg90(uint bcm_pin) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(50), PIN(bcm_pin) {
        initialize();
    }

    private: 

    /**
     * @brief Sets position in microseconds. This method is blocking 
     * and will delay by a specified period of milliseconds.
     * @param us (int) : The specified microsecond position
     * @param delay_millis (int) : The specified millisecond delay
     */
    void set(int us, int delay_millis) {
        lgTxServo(HANDLER, PIN, us, FREQUENCY, 0, 0); 
        lguSleep(std::max((double)MIN_MILLIS / 1000.0, (double)delay_millis / 1000.0));
        lgTxPulse(HANDLER, PIN, 0, 0, 0, 0);
    }

    /**
     * @brief Sets position in microseconds and holds position. This method is blocking 
     * and will delay by a specified period of milliseconds.
     * @param us (int) : The specified microsecond position
     * @param delay_millis (int) : The specified millisecond delay
     */
    void setHold(int us, int delay_millis) {
        lgTxServo(HANDLER, PIN, us, FREQUENCY, 0, 0); 
        lguSleep(std::max((double)MIN_MILLIS / 1000.0, (double)delay_millis / 1000.0));
    }

    /**
     * @brief Sets the motor position in degrees within an interval of [-90, +90]
     * @param degrees (int) : The specified degrees 
     */ 
    void setPosition(int degrees) {
        degrees = std::max(-90, std::min(90, degrees));
        set(microsecondPosition(degrees), 250);
    }

    /**
     * @brief Converts degree position to microseconds position. 
     * @note Degrees is within an interval of [-90. +90]
     * @param degrees (int) : The specified degrees
     * @returns The mapped microsecond position
     */
    int microsecondPosition(int degrees) {
        degrees = std::max(-90, std::min(90, degrees)) + 90;
        return (((double)degrees / 180.0) + 1.0) * 1000;
    }

    public: 

    /**
     * @brief Drives the motor to a specified position
     */
    void smoothDrive(int degrees, int millis) {
        double step_degrees = 10.0; // optimal minimial step size in degrees 
        // too large step-size: no motion control; too small: motor jitters and motion is unstable (hardware limit)
        double delta_degrees = degrees - this->current_position;
        double sgn = (delta_degrees >= 0) ? 1.0 : -1.0;
        double init_degrees = this->current_position;

        int partitions = std::floor(std::abs(delta_degrees) / step_degrees);
        for(int i=0; i<partitions; i++) {
            if(i+1 < partitions) { // running through all steps 
                double pos_i = (sgn*i*step_degrees) + init_degrees;
                setHold(microsecondPosition(pos_i), millis);
                this->current_position = pos_i;
            } else if(i+1 == partitions) { // running last step 
                set(microsecondPosition(degrees), millis);
                this->current_position = degrees;
            }
        }
    }

    /**
     * @brief Retrieves the current feed-forward position in degrees. 
     * @returns The current position
     */
    int getPosition() {
        return this->current_position;
    }
};

ServoSg90 servo = ServoSg90(18, 50);

int main() {
    sleep(1);
    while(1) {
        servo.smoothDrive(90, 20);
        sleep(1);
        servo.smoothDrive(-90, 20);
        sleep(1);
    }
    return 0;
}
