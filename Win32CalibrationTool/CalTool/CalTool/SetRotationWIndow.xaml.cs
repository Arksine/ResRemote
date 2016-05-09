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

namespace CalTool
{
    /// <summary>
    /// Interaction logic for SetRotationWIndow.xaml
    /// </summary>
    public partial class SetRotationWindow : Window
    {
        private ArduinoCom arduino;

        public SetRotationWindow()
        {          
            InitializeComponent();
            arduino = new ArduinoCom(onConnected, null, null, onFinished);
            arduino.connect();
        }

        private void onConnected(bool success)
        {
            if (success)
            {
                this.Dispatcher.Invoke((Action)(() =>
                {
                    tblStatus.Text = "Connected.  Setting Rotation";
                }));
                arduino.setOnlyRotation();
            }
            else
            {
                arduino.disconnect();

                this.Dispatcher.Invoke((Action)(() =>
                {
                    tblStatus.Text = "Error connecting to arduino";
                    // add a delayed task to close the calibration window
                    Task closeWindow = Task.Delay(3000).ContinueWith(_ => {

                        this.Dispatcher.Invoke(this.Close);
                    });
                }));
            }
        }

        private void onFinished(bool success)
        {
            if (success)
            {
                this.Dispatcher.Invoke((Action)(() =>
                {
                    tblStatus.Text = "Rotation Set!  Window will close momentarily";
                }));
            }
            else
            {
                this.Dispatcher.Invoke((Action)(() =>
                {
                    tblStatus.Text = "Error setting rotation value";
                }));
            }

            arduino.disconnect();

            this.Dispatcher.Invoke((Action)(() =>
            {
                // add a delayed task to close the calibration window
                Task closeWindow = Task.Delay(3000).ContinueWith(_ =>
                {

                    this.Dispatcher.Invoke(this.Close);
                });
            }));
        }

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            arduino.disconnect();
        }
    }


}
