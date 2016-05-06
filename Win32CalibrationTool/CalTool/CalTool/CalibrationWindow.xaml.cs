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
      
        private Storyboard animationStoryboard;
        private DoubleAnimation xAnimation;
        private DoubleAnimation yAnimation;
        private System.Drawing.Rectangle screenBounds;
        private double radius;
        private Canvas mainPanel;
        private Path target;
        private Point[] screenPoints;
       

        public CalibrationWindow()
        {
            InitializeComponent();
            initWindow();
            getScreenPoints();
            setupAnimation();
            animateToNext(screenPoints[1].X - radius, screenPoints[1].Y - radius);
            
        }

        private void initWindow()
        {
            screenBounds = ExtensionsForWPF.GetScreen(this).Bounds;

            Button btnExit = new Button();
            btnExit.Content = "Exit";
            btnExit.Width = 40;
            btnExit.Click += this.btnExit_Click;
            Canvas.SetBottom(btnExit, 50);
            Canvas.SetLeft(btnExit, 50);
           

            var brushConverter = new BrushConverter();
            var blueBrush = (Brush)brushConverter.ConvertFromString("#FF547DD4");

            mainPanel = new Canvas();
            mainPanel.Width = screenBounds.Width;
            mainPanel.Height = screenBounds.Height;
            mainPanel.Background = blueBrush;
            mainPanel.Children.Add(btnExit);
            this.Content = mainPanel;

        }

        private void getScreenPoints()
        {
            

            double xOffset = screenBounds.Width / 10;
            double yOffset = screenBounds.Height / 10;
            double xMax = screenBounds.Right - 1;
            double yMax = screenBounds.Bottom - 1;

            screenPoints = new Point[3];
            screenPoints[0] = new Point (xMax - xOffset, yMax / 2);
            screenPoints[1] = new Point(xMax / 2, yMax - yOffset);
            screenPoints[2] = new Point(xOffset - 1, yOffset - 1);

            if (xOffset < yOffset)
            {
                createGeometricPath(xOffset);
            }
            else
            {
                createGeometricPath(yOffset);
            }
        }

        private void createGeometricPath(double shapeSize)
        {
            target = new Path();
            target.Stroke = Brushes.Black;

            radius = shapeSize / 2;
            double xStart = shapeSize - 1;
            double yStart = screenPoints[0].Y - radius; 

            PathFigure horizontal = new PathFigure();
            horizontal.StartPoint = new Point(0, radius);
            horizontal.Segments.Add(
                new LineSegment(new Point(shapeSize, radius), true));

            // TODO:  This isn't working.  Probably need to add arcs in segments
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

            Canvas.SetLeft(target, screenPoints[0].X - radius);
            Canvas.SetTop(target, screenPoints[0].Y - radius);
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

        }

        private void animateToNext(double newX, double newY)
        {

            xAnimation.To = newX;
            yAnimation.To = newY;


            animationStoryboard.Begin(this);
        }


        private void btnExit_Click(object sender, RoutedEventArgs e)
        {
            this.Close();
        }

        private void btnTest_Click(object sender, RoutedEventArgs e)
        {
        }
    }

    public class MyPoint
    {
        public MyPoint(int xIn, int yIn)
        {
            this.x = xIn;
            this.y = yIn;
        }

        public int x { get; set; }
        public int y { get; set; }
    }
   

    static class ExtensionsForWPF
    {
        public static System.Windows.Forms.Screen GetScreen(this Window window)
        {
            return System.Windows.Forms.Screen.FromHandle(new WindowInteropHelper(window).Handle);
        }
    }
}
