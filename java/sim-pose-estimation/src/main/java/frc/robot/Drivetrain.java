// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.PWMVictorSPX;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.controller.PIDController;
import edu.wpi.first.wpilibj.controller.SimpleMotorFeedforward;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveKinematics;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveWheelSpeeds;

/**
 * Implements a controller for the drivetrain.
 * Converts a set of chassis motion commands into motor controller PWM values
 * which attempt to speed up or slow down the wheels to match the desired speed.
 */
public class Drivetrain {


  // PWM motor controller output definitions
  PWMVictorSPX m_leftLeader = new PWMVictorSPX(Constants.kDtLeftLeaderPin);
  PWMVictorSPX m_leftFollower = new PWMVictorSPX(Constants.kDtLeftFollowerPin);
  PWMVictorSPX m_rightLeader = new PWMVictorSPX(Constants.kDtRightLeaderPin);
  PWMVictorSPX m_rightFollower = new PWMVictorSPX(Constants.kDtRightFollowerPin);

  SpeedControllerGroup m_leftGroup = new SpeedControllerGroup(m_leftLeader, m_leftFollower);
  SpeedControllerGroup m_rightGroup = new SpeedControllerGroup(m_rightLeader, m_rightFollower);

  // Drivetrain wheel speed sensors
  // Used both for speed control and pose estimation.
  Encoder m_leftEncoder = new Encoder(Constants.kDtLeftEncoderPinA, Constants.kDtLeftEncoderPinB);
  Encoder m_rightEncoder = new Encoder(Constants.kDtRightEncoderPinA, Constants.kDtRightEncoderPinB);

  // Drivetrain Pose Estimation
  DrivetrainPoseEstimator poseEst = new DrivetrainPoseEstimator();

  // Kinematics - defines the physical size and shape of the drivetrain, which is required to convert from
  // chassis speed commands to wheel speed commands.
  DifferentialDriveKinematics m_kinematics =
      new DifferentialDriveKinematics(Constants.kTrackWidth);

  // Closed-loop PIDF controllers for servoing each side of the drivetrain to a specific speed.
  // Gains are for example purposes only - must be determined for your own robot!
  SimpleMotorFeedforward m_feedforward = new SimpleMotorFeedforward(1, 3);
  PIDController m_leftPIDController = new PIDController(8.5, 0, 0);
  PIDController m_rightPIDController = new PIDController(8.5, 0, 0);

  public Drivetrain() {
    // Set the distance per pulse for the drive encoders. We can simply use the
    // distance traveled for one rotation of the wheel divided by the encoder
    // resolution.
    m_leftEncoder.setDistancePerPulse(2 * Math.PI * Constants.kWheelRadius / Constants.kEncoderResolution);
    m_rightEncoder.setDistancePerPulse(2 * Math.PI * Constants.kWheelRadius / Constants.kEncoderResolution);

    m_leftEncoder.reset();
    m_rightEncoder.reset();

    m_rightGroup.setInverted(true);

  }

  /**
   * Given a set of chassis (fwd/rev + rotate) speed commands, perform all periodic tasks to 
   * assign new outputs to the motor controllers.
   * @param xSpeed Desired chassis Forward or Reverse speed (in meters/sec). Positive is forward.
   * @param rot Desired chassis rotation speed in radians/sec. Positive is counter-clockwise.
   */
  public void drive(double xSpeed, double rot) {
    // Convert our fwd/rev and rotate commands to wheel speed commands
    DifferentialDriveWheelSpeeds speeds = m_kinematics.toWheelSpeeds(new ChassisSpeeds(xSpeed, 0, rot));
    
    // Calculate the feedback (PID) portion of our motor command, based on desired wheel speed
    var leftOutput =
        m_leftPIDController.calculate(m_leftEncoder.getRate(), speeds.leftMetersPerSecond);
    var rightOutput =
        m_rightPIDController.calculate(m_rightEncoder.getRate(), speeds.rightMetersPerSecond);

    // Calculate the feedforward (F) portion of our motor command, based on desired wheel speed
    var leftFeedforward = m_feedforward.calculate(speeds.leftMetersPerSecond);
    var rightFeedforward = m_feedforward.calculate(speeds.rightMetersPerSecond);

    // Update the motor controllers with our new motor commands
    m_leftGroup.setVoltage(leftOutput + leftFeedforward);
    m_rightGroup.setVoltage(rightOutput + rightFeedforward);

    // Update the pose estimator with the most recent sensor readings.
    poseEst.update(new DifferentialDriveWheelSpeeds(m_leftEncoder.getRate(), m_rightEncoder.getRate()), m_leftEncoder.getDistance(), m_rightEncoder.getDistance());
  }

  /**
   * Force the pose estimator and all sensors to a particular pose. This is useful for 
   * indicating to the software when you have manually moved your robot in a particular 
   * position on the field (EX: when you place it on the field at the start of the match).
   * @param pose
   */
  public void resetOdometry(Pose2d pose) {
    m_leftEncoder.reset();
    m_rightEncoder.reset();
    poseEst.resetToPose(pose);
  }

  /**
   * @return The current best-guess at drivetrain Pose on the field.
   */
  public Pose2d getCtrlsPoseEstimate() {
    return poseEst.getPoseEst();
  }

}
