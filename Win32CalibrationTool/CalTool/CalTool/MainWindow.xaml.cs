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

            try
            {

                ManagementObjectSearcher searcher =
                    new ManagementObjectSearcher("root\\CIMV2",
                    "SELECT * FROM Win32_SerialPort");



                foreach (String name in portNames)
                {
                    string description = name;
                    foreach (ManagementObject queryObj in searcher.Get())
                    {
                        if (name.Equals(queryObj["DeviceID"].ToString()))
                        {
                            description = queryObj["Name"].ToString();
                        }

                    }
                    this.cbxSelectDevice.Items.Add(new ComPortItem() { Name = description, Value = name });
                }
            }
            catch (ManagementException e)
            {
                MessageBox.Show("An error occurred while querying for WMI data: " + e.Message);
            }
        }

        private void btnRefresh_Click(object sender, RoutedEventArgs e)
        {
            enumerateSerialPorts();
        }

        private void btnStartCalibration_Click(object sender, RoutedEventArgs e)
        {
            String device;
            bool? deviceType = rbtHid.IsChecked;
            if (deviceType == null)
            {
                return;
            }
            try {
                ComPortItem port = (ComPortItem)this.cbxSelectDevice.SelectedValue;
                device = port.Value;
            }
            catch (NullReferenceException error)
            {
                // TODO: uncomment after testing
                //MessageBox.Show("No COM port selected");
                //return;
            }

            // TODO: add parameters to calwindows constructor so I can pass values
            CalibrationWindow calWin = new CalibrationWindow();
            calWin.Show();
        }
    }
}
