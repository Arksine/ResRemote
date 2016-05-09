using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Media.Animation;
using System.Windows.Interop;

namespace CalTool
{
    /// <summary>
    /// Interaction logic for CalibrationWindow.xaml
    /// </summary>
    public partial class CalibrationWindow : Window
    {
        private ArduinoCom arduino;
        private Storyboard animationStoryboard;
        private Storyboard textFadeOut;
        private Storyboard textFadeIn;
        private DoubleAnimation xAnimation;
        private DoubleAnimation yAnimation;
        private DoubleAnimation textMoveAnimation;
        private System.Drawing.Rectangle screenBounds;
        private Canvas mainPanel;
        TextBlock textBlock;
        private Path target;
        private Point[] screenPoints;
        private volatile int pointIndex = 0;
       

        public CalibrationWindow()
        {
            arduino = new ArduinoCom(onConnectionEstablished, onPointReceived, 
                onPressureReceived, onCalibrationComplete);

            InitializeComponent();
            initWindow();
            getScreenPoints();
            setupAnimation();

            arduino.connect();
                      
        }

        private void initWindow()
        {
            screenBounds = ExtensionsForWPF.GetScreen(this).Bounds;

            var brushConverter = new BrushConverter();
            var blueBrush = (Brush)brushConverter.ConvertFromString("#FF547DD4");

            Button btnExit = new Button();
            btnExit.Content = "Exit";
            btnExit.Width = 40;
            btnExit.Click += this.btnExit_Click;
            Canvas.SetBottom(btnExit, 50);
            Canvas.SetLeft(btnExit, 50);

            textBlock = new TextBlock();
            textBlock.TextWrapping = TextWrapping.Wrap;
            textBlock.Width = screenBounds.Width / 2;
            textBlock.Height = 60;
            textBlock.TextAlignment = TextAlignment.Center;
            textBlock.FontSize = 20;
            textBlock.Background = blueBrush;
            textBlock.Foreground = Brushes.White;

            textBlock.Inlines.Add(new Run("Connecting to device..."));
            Canvas.SetLeft(textBlock, screenBounds.Width / 4);
            Canvas.SetTop(textBlock, (screenBounds.Height / 2) - (textBlock.Height / 2));

            mainPanel = new Canvas();
            mainPanel.Width = screenBounds.Width;
            mainPanel.Height = screenBounds.Height;
            mainPanel.Background = blueBrush;
            mainPanel.Children.Add(btnExit);
            mainPanel.Children.Add(textBlock);
            this.Content = mainPanel;

        }

        private void getScreenPoints()
        {           
            double xOffset = screenBounds.Width / 10;
            double yOffset = screenBounds.Height / 10;
            double xMax = screenBounds.Right - 1;
            double yMax = screenBounds.Bottom - 1;

            double shapeSize;
            if (xOffset < yOffset)
            {
                shapeSize = xOffset;
            }
            else
            {
                shapeSize = yOffset;
            }

            double radius = shapeSize / 2;

            screenPoints = new Point[4];
            screenPoints[0] = new Point (xMax - xOffset - radius, yMax / 2 - radius);
            screenPoints[1] = new Point(xMax / 2 - radius, yMax - yOffset - radius);
            screenPoints[2] = new Point(xOffset - 1 - radius, yOffset - 1 - radius);
            screenPoints[3] = new Point(xMax / 2 - radius, yMax / 2 - radius);

            createGeometricPath(shapeSize);
        }

        
        private void createGeometricPath(double shapeSize)
        {
            target = new Path();
            target.Stroke = Brushes.Black;

            double radius = shapeSize / 2;
           
            PathFigure horizontal = new PathFigure();
            horizontal.StartPoint = new Point(0, radius);
            horizontal.Segments.Add(
                new LineSegment(new Point(shapeSize, radius), true));

            horizontal.Segments.Add(
                new ArcSegment(new Point(0, radius),
                new Size(radius, radius), 45, true, SweepDirection.Clockwise, true));
            horizontal.Segments.Add(
                new ArcSegment(new Point(shapeSize, radius),
                new Size(radius, radius), 45, true, SweepDirection.Clockwise, true));


            PathFigure vertical = new PathFigure();
            vertical.StartPoint = new Point(radius, 0);
            vertical.Segments.Add(
                new LineSegment(new Point(radius, shapeSize), true));

            PathGeometry tempGeometry = new PathGeometry();
            tempGeometry.Figures.Add(horizontal);
            tempGeometry.Figures.Add(vertical);

            target.StrokeThickness = 2;
            target.Data = tempGeometry;
            target.Fill = Brushes.White;
            target.Visibility = Visibility.Hidden;

            Canvas.SetLeft(target, screenPoints[0].X);
            Canvas.SetTop(target, screenPoints[0].Y);
            this.mainPanel.Children.Add(target);

        }

        private void setupAnimation()
        {
            Duration duration = new Duration(TimeSpan.FromSeconds(1));
            xAnimation = new DoubleAnimation();
            yAnimation = new DoubleAnimation();
            xAnimation.Duration = duration;
            yAnimation.Duration = duration;
            

            Storyboard.SetTarget(xAnimation, target);
            Storyboard.SetTarget(yAnimation, target);
            Storyboard.SetTargetProperty(xAnimation,
                new PropertyPath(Canvas.LeftProperty));
            Storyboard.SetTargetProperty(yAnimation,
                new PropertyPath(Canvas.TopProperty));

            animationStoryboard = new Storyboard();
            animationStoryboard.Children.Add(xAnimation);
            animationStoryboard.Children.Add(yAnimation);

            // setup text fade animation
            DoubleAnimation fadeOutAnimation = 
                new DoubleAnimation(1.0, 0.0, new Duration(TimeSpan.FromSeconds(.5)));
            DoubleAnimation fadeInAnimation = 
                new DoubleAnimation(0.0, 1.0, new Duration(TimeSpan.FromSeconds(.5)));
            fadeOutAnimation.Completed += delegate (object sender, EventArgs e)
            {
                if (pointIndex == 0)
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Calibrate First Point: \n"));
                    textBlock.Inlines.Add(new Run("Touch the indicated target"));
                }
                else if (pointIndex == 1)
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Calibrate Second Point: \n"));
                    textBlock.Inlines.Add(new Run("Touch the indicated target"));
                }
                else if (pointIndex == 2)
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Calibrate Third Point: \n"));
                    textBlock.Inlines.Add(new Run("Touch the indicated target"));
                }
                else if (pointIndex == 3)
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Calibrate Pressure: \n"));
                    textBlock.Inlines.Add(new Run("Apply pressure to the target, then slowly release"));
                } 
                else if (pointIndex == 4)
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Finishing Calibration..."));
                    
                }
                else
                {
                    textBlock.Inlines.Clear();
                    textBlock.Inlines.Add(new Run("Calibration Complete! \n"));
                    textBlock.Inlines.Add(new Run("This window will automatically close momentarily"));
                }              
                textFadeIn.Begin();
            };

            Storyboard.SetTarget(fadeOutAnimation, textBlock);
            Storyboard.SetTarget(fadeInAnimation, textBlock);
            Storyboard.SetTargetProperty(fadeOutAnimation,
                new PropertyPath(TextBlock.OpacityProperty));
            Storyboard.SetTargetProperty(fadeInAnimation,
                new PropertyPath(TextBlock.OpacityProperty));
            textFadeOut = new Storyboard();
            textFadeOut.Children.Add(fadeOutAnimation);
            textFadeIn = new Storyboard();
            textFadeIn.Children.Add(fadeInAnimation);

        }


        private void btnExit_Click(object sender, RoutedEventArgs e)
        {
            arduino.disconnect();
            this.Close();
        }

        
        #region CALLBACKS
        private void onConnectionEstablished(bool connected)
        {
            if (!connected)
            {
                MessageBox.Show("Failed to connect to com port: " + GlobalPreferences.comPort);
                arduino.disconnect();
                this.Dispatcher.Invoke(this.Close);
                return;
            }

            arduino.startCalibrationThread();

            // We are connected, show the touch target
            this.Dispatcher.Invoke((Action)(() =>
            {
                textFadeOut.Begin();
                target.Visibility = Visibility.Visible;
            }));
        }

        private void onPointReceived(bool success, int nextIndex)
        {
            if (!success)
            {
                MessageBox.Show("Failed to read calibration point");
                arduino.disconnect();
                this.Dispatcher.Invoke(this.Close);
                return;
            }

            pointIndex = nextIndex;

            if (pointIndex == 3)
            {
                // We are calculating pressure here, so we need to move the text.  Animate it.
                this.Dispatcher.Invoke((Action)(() =>
                {
                    textMoveAnimation = new DoubleAnimation();
                    textMoveAnimation.To = screenBounds.Height / 4;
                    textMoveAnimation.Duration = new Duration(TimeSpan.FromSeconds(1));
                    Storyboard.SetTarget(textMoveAnimation, textBlock);
                    Storyboard.SetTargetProperty(textMoveAnimation,
                        new PropertyPath(Canvas.TopProperty));
                    animationStoryboard.Children.Add(textMoveAnimation);
                }));

            }
            else if (pointIndex > 3)
            {
                // this is an error
                MessageBox.Show("Invalid point received");
                arduino.disconnect();
                this.Dispatcher.Invoke(this.Close);
                return;
            }

            this.Dispatcher.Invoke((Action)(() =>
            {
                xAnimation.To = screenPoints[pointIndex].X;
                yAnimation.To = screenPoints[pointIndex].Y;
                animationStoryboard.Begin(this);
                textFadeOut.Begin();
            }));

        }

        private void onPressureReceived(bool success)
        {
            if (!success)
            {
                MessageBox.Show("Failed to read point resistance");
                arduino.disconnect();
                this.Dispatcher.Invoke(this.Close);
                return;
            }

            // We are done animating the touch point target, now we need to move text back to the center
            this.Dispatcher.Invoke((Action)(() =>
            {
                pointIndex = 4;
                target.Visibility = Visibility.Hidden;
                textMoveAnimation.To = (screenBounds.Height / 2) - (textBlock.Height / 2);
                animationStoryboard.Children.Clear();       // clear the animation storyboard, as we dont need to animate the targe
                animationStoryboard.Children.Add(textMoveAnimation);
                animationStoryboard.Begin(this);
                textFadeOut.Begin();
            }));
        }

        private void onCalibrationComplete(bool success)
        {
            if (!success)
            {
                MessageBox.Show("Failed to complete calibration");
                arduino.disconnect();
                this.Dispatcher.Invoke(this.Close);
                return;
            }

            arduino.disconnect();

            this.Dispatcher.Invoke((Action)(() =>
            {
                pointIndex = 5;
                textFadeOut.Begin();

                // add a delayed task to close the calibration window
                Task closeWindow = Task.Delay(3000).ContinueWith(_ =>
                {

                    this.Dispatcher.Invoke(this.Close);
                });
            }));
        }

        #endregion CALLBACKS
    }

    static class ExtensionsForWPF
    {
        public static System.Windows.Forms.Screen GetScreen(this Window window)
        {
            return System.Windows.Forms.Screen.FromHandle(new WindowInteropHelper(window).Handle);
        }
    }
}
