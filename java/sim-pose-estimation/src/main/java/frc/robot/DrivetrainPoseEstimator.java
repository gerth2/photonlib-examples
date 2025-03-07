package frc.robot;

import org.photonvision.PhotonCamera;

import edu.wpi.first.wpilibj.AnalogGyro;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.estimator.DifferentialDrivePoseEstimator;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.geometry.Transform2d;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveWheelSpeeds;
import edu.wpi.first.wpilibj.util.Units;
import edu.wpi.first.wpiutil.math.Matrix;
import edu.wpi.first.wpiutil.math.VecBuilder;
import edu.wpi.first.wpiutil.math.numbers.N1;
import edu.wpi.first.wpiutil.math.numbers.N3;
import edu.wpi.first.wpiutil.math.numbers.N5;

/**
 * Performs estimation of the drivetrain's current position on the field, using
 * a vision system, drivetrain encoders, and a gyroscope. These sensor readings
 * are fused together using a Kalman filter. This in turn creates a best-guess
 * at a Pose2d of where our drivetrain is currently at.
 */
public class DrivetrainPoseEstimator {

    // Sensors used as part of the Pose Estimation
    private final AnalogGyro gyro = new AnalogGyro(Constants.kGyroPin);
    private PhotonCamera cam = new PhotonCamera(Constants.kCamName);
    // Note - drivetrain encoders are also used. The Drivetrain class must pass us
    // the relevant readings.

    // Kalman Filter Configuration. These can be "tuned-to-taste" based on how much
    // you trust your
    // various sensors. Smaller numbers will cause the filter to "trust" the
    // estimate from that particular
    // component more than the others. This in turn means the particualr component
    // will have a stronger
    // influence on the final pose estimate.
    Matrix<N5, N1> stateStdDevs = VecBuilder.fill(0.05, 0.05, Units.degreesToRadians(5), 0.05, 0.05);
    Matrix<N3, N1> localMeasurementStdDevs = VecBuilder.fill(0.01, 0.01, Units.degreesToRadians(0.1));
    Matrix<N3, N1> visionMeasurementStdDevs = VecBuilder.fill(0.01, 0.01, Units.degreesToRadians(0.1));

    private final DifferentialDrivePoseEstimator m_poseEstimator = new DifferentialDrivePoseEstimator(
            gyro.getRotation2d(), new Pose2d(), stateStdDevs, localMeasurementStdDevs, visionMeasurementStdDevs);

    public DrivetrainPoseEstimator() {

    }

    /**
     * Perform all periodic pose estimation tasks.
     * 
     * @param actWheelSpeeds Current Speeds (in m/s) of the drivetrain wheels
     * @param leftDist       Distance (in m) the left wheel has traveled
     * @param rightDist      Distance (in m) the right wheel has traveled
     */
    public void update(DifferentialDriveWheelSpeeds actWheelSpeeds, double leftDist, double rightDist) {

        m_poseEstimator.update(gyro.getRotation2d(), actWheelSpeeds, leftDist, rightDist);

        var res = cam.getLatestResult();
        if (res.hasTargets()) {
            double imageCaptureTime = Timer.getFPGATimestamp() - res.getLatencyMillis();
            Transform2d camToTargetTrans = res.getBestTarget().getCameraToTarget();
            Pose2d camPose = Constants.kFarTargetPose.transformBy(camToTargetTrans.inverse());
            m_poseEstimator.addVisionMeasurement(camPose.transformBy(Constants.kCameraToRobot), imageCaptureTime);
        }
    }

    /**
     * Force the pose estimator to a particular pose. This is useful for indicating
     * to the software when you have manually moved your robot in a particular
     * position on the field (EX: when you place it on the field at the start of the
     * match).
     * 
     * @param pose
     */
    public void resetToPose(Pose2d pose) {
        m_poseEstimator.resetPosition(pose, gyro.getRotation2d());
    }

    /**
     * @return The current best-guess at drivetrain position on the field.
     */
    public Pose2d getPoseEst() {
        return m_poseEstimator.getEstimatedPosition();
    }

}
