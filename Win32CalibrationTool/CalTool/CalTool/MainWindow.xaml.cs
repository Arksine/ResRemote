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
using System.Windows.Navigation;
using System.Windows.Shapes;
using System.Management;
using System.IO.Ports;

namespace CalTool
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        public class ComPortItem
        {
            public string Name { get; set; }
            public string Value { get; set; }
            public override string ToString() { return this.Name; }
        }

        public MainWindow()
        {
            InitializeComponent();

        }

        private void enumerateSerialPorts()
        {
            String[] portNames = SerialPort.GetPortNames();

            if (portNames == null || portNames.Length == 0)
            {
                // TODO: add "No device found" to list and return
                this.cbxSelectDevice.Items.Add(new ComPortItem() { Name = "No devices found", Value = "NO_DEVICE" });
                return;
            }

            if (ckbComDetails.IsChecked == true)
            {
                // The code below queries WMI to retrieve actual serial port descriptions.  The problem
                // is that it is VERY slow (takes up to 10 seconds).  Need to see if there is a faster way to do this
                try
                {
                    ManagementObjectSearcher searcher =
                        new ManagementObjectSearcher("root\\CIMV2",
                        "SELECT * FROM Win32_SerialPort");

                    string devId;
                    string description;
                    foreach (ManagementObject queryObj in searcher.Get())
                    {    
                        devId = queryObj["DeviceID"].ToString();
                        description = queryObj["Name"].ToString();                    
                        this.cbxSelectDevice.Items.Add(new ComPortItem() { Name = description, Value = devId });
                    }                                       
                }
                catch (ManagementException e)
                {
                    MessageBox.Show("An error occurred while querying for WMI data: " + e.Message);
                }
            }
            else {
                foreach (String name in portNames)
                {
                    this.cbxSelectDevice.Items.Add(new ComPortItem() { Name = name, Value = name });
                }
            }
        }

        private void btnRefresh_Click(object sender, RoutedEventArgs e)
        {
            this.cbxSelectDevice.Items.Clear();
            enumerateSerialPorts();
        }

        private void btnStartCalibration_Click(object sender, RoutedEventArgs e)
        {
            
            if (rbtHid.IsChecked == true)
            {
                GlobalPreferences.devType = DeviceType.HID;
            }
            else if (rbtUinput.IsChecked == true)
            {
                GlobalPreferences.devType = DeviceType.UINPUT;
                GlobalPreferences.deviceWidth = int.Parse(tbxWidth.Text);
                GlobalPreferences.deviceHeight = int.Parse(tbxHeight.Text);
            }
            else
            {
                GlobalPreferences.devType = DeviceType.HID;
                GlobalPreferences.deviceWidth = 10000;
                GlobalPreferences.deviceHeight = 10000;
            }

            if (rbtRot0.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_0;
            }
            else if (rbtRot90.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_90;
            }
            else if (rbtRot180.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_180;
            }
            else if (rbtRot270.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_270;
            }
            else
            {
                GlobalPreferences.rotation = Rotation.ROTATION_0;
            }

            try {
                ComPortItem port = (ComPortItem)this.cbxSelectDevice.SelectedValue;
                GlobalPreferences.comPort = port.Value;
            }
            catch (NullReferenceException error)
            {
                // TODO: uncomment after testing
                MessageBox.Show("No COM port selected");
                return;
            }

            // TODO: add parameters to calwindows constructor so I can pass values
            CalibrationWindow calWin = new CalibrationWindow();
            calWin.Show();
        }

        private void btnChangeOrientaton_Click(object sender, RoutedEventArgs e)
        {
            if (rbtRot0.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_0;
            }
            else if (rbtRot90.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_90;
            }
            else if (rbtRot180.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_180;
            }
            else if (rbtRot270.IsChecked == true)
            {
                GlobalPreferences.rotation = Rotation.ROTATION_270;
            }
            else
            {
                GlobalPreferences.rotation = Rotation.ROTATION_0;
            }

            try
            {
                ComPortItem port = (ComPortItem)this.cbxSelectDevice.SelectedValue;
                GlobalPreferences.comPort = port.Value;
            }
            catch (NullReferenceException error)
            {
                // TODO: uncomment after testing
                MessageBox.Show("No COM port selected");
                return;
            }

            SetRotationWindow rotationWin = new SetRotationWindow();
            rotationWin.Show();
        }
    }
}
