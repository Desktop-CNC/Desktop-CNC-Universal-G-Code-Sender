#include <iostream>
#include <lgpio.h>
#include <unistd.h>

class ServoSg90 {
    private:
    const uint CHIP = 4;
    const int HANDLER;
    const int FREQUENCY;
    const uint PIN;
    int current_position = 90;
    
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
        // move to initial position
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

    /**
     * @brief Sets position in microseconds. This method is blocking 
     * and will delay by a specified period of milliseconds.
     * @param us (int) : The specified microsecond position
     * @param delay_millis (int) : The specified millisecond delay
     */
    void set(int us, int delay_millis) {
        lgTxServo(HANDLER, PIN, us, FREQUENCY, 0, 0); 
        lguSleep((double)delay_millis / 1000.0);
        lgTxPulse(HANDLER, PIN, 0, 0, 0, 0);
    }

    /**
     * @brief Sets the motor position in degrees within an interval of [-90, +90]
     * @param degrees (int) : The specified degrees 
     */ 
    void setPosition(int degrees) {
        degrees = std::max(-90, std::min(90, degrees));
        set(microsecondPosition(degrees), 200);
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

    /**
     * @brief Moves the motor to the specified position.
     */
    void moveToPosition(int degrees) {
         
    }
};

ServoSg90 servo = ServoSg90(18, 50);

int main() {
    servo.setPosition(-90);  
    sleep(2);
    servo.setPosition(90);

    
    return 0;
}
