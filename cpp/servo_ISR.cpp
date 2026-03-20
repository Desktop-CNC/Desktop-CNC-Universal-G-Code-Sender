#include <iostream>
#include <lgpio.h>
#include <unistd.h>

class ServoSg90 {
    private:
    // servo library parameters 
    const uint CHIP = 4;
    const int HANDLER;
    const int FREQUENCY;
    const uint PIN;
    // servo motion profile parameters 
    double ACCELERATION = 0.0; 
    double SPEED_MAX = 1.0;
    double ACCELERATION_DISTANCE = 0.0;
    // feed-forward position
    double current_position = 90;
    
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
        // move to initial motion profile/position
        setMotionProfile(0.1, 0.01);
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
     * @brief Computes the speed on a trapezoidal motion profile given a 
     * current position between a known start and end position angles in degrees. 
     * @param position (double) : The specified current position
     * @param p0 (double) : The starting position
     * @param p1 (double) : The end position
     * @returns The computed speed
     */
    double speed(double position, double p0, double p1) {
        double total_distance = std::abs(p1-p0);
        double traveled_distance = std::abs(position-p0);
        double remaining_distance = std::abs(p1-position);

        double p_plus = std::min(this->ACCELERATION_DISTANCE, total_distance / 2.0);
        double p_minus = total_distance - p_plus;

        if(traveled_distance < p_plus) {
            return std::pow(2.0*this->ACCELERATION * (traveled_distance + 0.1), 0.5);
        } else if(traveled_distance >= p_plus && traveled_distance < p_minus) {
            return this->SPEED_MAX;
        } else {
            return std::pow(2.0*this->ACCELERATION * (remaining_distance + 0.1), 0.5);
        }
    } 

    public: 

    /**
     * @brief Sets the motion profile parameters. 
     * @param MAX_SPEED (double) : The specified maximum speed 
     * @param ACCELERATION (double) : The specified acceleration
     */
    void setMotionProfile(double MAX_SPEED, double ACCELERATION) {
        this->SPEED_MAX = std::max(0.1, MAX_SPEED); 
        this->ACCELERATION = std::max(0.01, ACCELERATION);
        this->ACCELERATION_DISTANCE = (std::pow(this->SPEED_MAX, 2.0)/(2.0*this->ACCELERATION));
    }

    /**
     * @brief Moves the motor to the specified position.
     * @note This method is blocking.
     */
    void moveToPosition(int degrees) {
        // parameters for motion
        double p0 = (double)this->current_position;
        double p1 = (double)degrees;
        double sgn = (p0 <= p1) ? 1.0 : -1.0;
        // partition parameters 
        double step = 1.0;
        int partitions = (int)(std::abs(p1 - p0) / step);

        for(int i=0; i<partitions; i++) {
            double position_i = p0 + (sgn*i*step);
            double speed_i = speed(position_i, p0, p1);
            double time_i = step / std::max(speed_i, 0.001);

            set(microsecondPosition(position_i));
            lguSleep(time_i);
            this->current_position = position_i;
        }
    }

    /**
     * @brief 
     * @param degrees (int) : 
     */
    void normMoveToPosition(int degrees) {
        // absolute position parameters 
        double p0 = (double)this->current_position; 
        double p1 = (double)degrees;
        double sgn = (p0 <= p1) ? 1.0 : -1.0;
        // calculate acceleration distance 
        double D = std::pow(SPEED_MAX, 2.0) / (2.0*ACCELERATION); 
        if(D > std::abs(p1 - p0)) {
            D = std::abs(p1 - p0) / 2.0;
        }

        // distance (d) to run at constant speed 
        double d = std::abs(p1 - p0) - (2.0*D);
        double delta_t = 0.1; // step time 

        // calculated times for acceleration 
        double t_accel = SPEED_MAX / ACCELERATION;
        double t_const = d / SPEED_MAX; 
        double t_total = (2.0*t_accel) + t_const;

        // motion parameters 
        double velocity = 0.0; 

        int partitions = (int)(t_total / delta_t);
        for(int i=0; i<partitions; i++) {
            if(time_i < t_accel) {
                // accelerate 
                velocity += sgn*delta_t*ACCELERATION;
            } else if(time_i < t_accel + t_const) {
                // constant speed
                velocity = sgn*SPEED_MAX;
            } else {
                // decelerate 
                velocity -= sgn*delta_t*ACCELERATION;
            }
            // integrate position and move
            this->current_position += velocity*delta_t;
            set(microsecondPosition(this->current_position), delta_t);
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
    servo.setMotionProfile(200, 1500);
    
    servo.moveToPosition(-90);
    sleep(2);
    servo.moveToPosition(90);

    return 0;
}
