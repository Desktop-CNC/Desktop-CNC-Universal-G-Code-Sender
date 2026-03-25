#include <iostream>
#include <lgpio.h>
#include <initializer_list>
#include <unistd.h>
#include <vector>
#include <thread>
#include <mutex>
#include <cmath>
#include <algorithm>

class ServoSg90 {
    private:
    // servo library parameters 
    const uint CHIP = 4;
    const int HANDLER;
    const int FREQUENCY;
    const uint PIN;
    // feed-forward parameters 
    double current_position = 0;
    const int MIN_MILLIS = 10; // minimum time to wait for servo to move 
    bool is_inverted = false; 

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
     * @param position (int) : The specified initial position set and to move to
     * @param freq (uint) : The specified servo motor frequency in Hertz
     */
    ServoSg90(uint bcm_pin, int position, uint freq) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(freq), PIN(bcm_pin) {
        initialize();
    }

    /**
     * @brief Creates a `ServoSg90` instance. 
     * @param bcm_pin (uint) : The specified Raspberry Pi BCM Pin
     * @param position (int) : The specified initial position set and to move to
     */
    ServoSg90(uint bcm_pin, int position) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(50), PIN(bcm_pin) {
        initialize();
    }

    /**
     * @brief Creates a `ServoSg90` instance. 
     * @param bcm_pin (uint) : The specified Raspberry Pi BCM Pin
     * @param position (int) : The specified initial position set and to move to
     * @param freq (uint) : The specified servo motor frequency in Hertz
     * @param invert (bool) : Whether or not to invert motor direction
     */
    ServoSg90(uint bcm_pin, int position, uint freq, bool invert) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(freq), PIN(bcm_pin), is_inverted(invert) {
        initialize();
    }

    /**
     * @brief Creates a `ServoSg90` instance. 
     * @param bcm_pin (uint) : The specified Raspberry Pi BCM Pin
     * @param position (int) : The specified initial position set and to move to
     * @param invert (bool) : Whether or not to invert motor direction 
     */
    ServoSg90(uint bcm_pin, int position, bool invert) 
        :   HANDLER(lgGpiochipOpen(CHIP)), FREQUENCY(50), PIN(bcm_pin), is_inverted(invert) {
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
        setHold(us, delay_millis);
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
        degrees = (this->is_inverted) ? -1*degrees : degrees;
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
     * @brief Sets if the motor directions are inverted. 
     * @note Clockwise is positive movement when looking at motor from facing the servo horn.
     * @param invert (bool) : Specified flag 
     */
    void setInvert(bool invert) {
        this->is_inverted = invert;
    }

    /**
     * @brief Retrieves inverted position status 
     * @returns Whether or not the motor direction is inverted
     */
    bool isInverted() {
        return this->is_inverted;
    }

    /**
     * @brief Retrieves the current feed-forward position in degrees. 
     * @returns The current position
     */
    int getPosition() {
        return this->current_position;
    }
};

/**
 * @brief Represents a ServoSg90 motor group where driving each motor is done asynchronously. 
 * Motors can be synced by calling each one of them to drive and then calling on a motor group dwell.
 */
class ServoSg90Group {
    private: 
    // unique lists of servos and mutexs for threaded servo drivings 
    std::vector<std::unique_ptr<ServoSg90>> servos;
    std::vector<std::unique_ptr<std::mutex>> mtxs;
    // active threads 
    std::vector<std::thread> active_threads;

    public: 
    
    /**
     * @brief Creates a `ServoSg90Group` instance. 
     * @param servos (std::vector<std::unique_ptr<ServoSg90>>) : The specified servos
     */
    ServoSg90Group(std::vector<std::unique_ptr<ServoSg90>> servos) 
        :   servos(std::move(servos)) {
       for(int i=0; i<this->servos.size(); i++) {
            mtxs.push_back(std::make_unique<std::mutex>());
       }
    }

    /**
     * @brief Runs a smooth-drive motion for the specified servo. 
     * @note This method is non-blocking! This is so other motors can be handled in parallel.
     * @param servo (uint) : The specified servo index to drive 
     * @param degrees (int) : The specified position to drive to 
     * @param millis (int) : The spceified amount of time between motion profile steps 
     */
    void smoothDrive(uint servo, int degrees, int millis) {
        if(servo < this->servos.size()) {
            this->active_threads.emplace_back(std::thread([this, servo, degrees, millis]() {
                // lock i-th servo on thread to drive it by i-th mutex
                std::lock_guard<std::mutex> lock(*(mtxs.at(servo)));
                servos.at(servo)->smoothDrive(degrees, millis);
            }));
        }
    }

    /**
     * @brief Dwells the servo motor group. This is a blocking method that ends once
     * all motors in the group have stopped moving.
     */
    void dwell() {
        for(auto& thread : active_threads) {
            if(thread.joinable()) 
                thread.join();
        }
        // clean thread buffer
        this->active_threads.clear();
    }
};

int main(int argc, char* argv[]) {
    // program arguments 
    int left_servo_pin = atoi(argv[1]);
    int right_servo_pin = atoi(argv[2]);
    int init_degrees = atoi(argv[3]);
    int final_degrees = atoi(argv(4)); 
    // left servo pin = 24
    // right servo pin = 23

    // motor group
    std::vector<std::unique_ptr<ServoSg90>> supplier;
    supplier.push_back(std::make_unique<ServoSg90>(left_servo_pin, init_degrees, 50, true)); // left motor
    supplier.push_back( std::make_unique<ServoSg90>(right_servo_pin, init_degrees, 50, false)); // right motor
    ServoSg90Group group = ServoSg90Group(std::move(supplier));

    // run servo motion
    group.smoothDrive(0, final_degrees, 20);
    group.smoothDrive(1, final_degrees, 20);
    group.dwell();
    return 0;
}
