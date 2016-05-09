using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.IO.Ports;
using System.Threading;
using System.Diagnostics;
namespace CalTool
{
    class ArduinoCom
    {
        private const int BAUDRATE = 9600;

        private Thread calibrateThread;
        private volatile bool mRunning = false;
        private SerialPort arduinoPort;

        // delegates for callbacks
        public delegate void ConnectionEstablished(bool success);
        public delegate void PointReceived(bool success, int nextIndex);
        public delegate void PressureReceived(bool success);
        public delegate void CalibrationComplete(bool success);

        private ConnectionEstablished connEstablished;
        private PointReceived ptRecd;
        private PressureReceived pressureRecd;
        private CalibrationComplete calComplete;

        private class ArduinoMessage
        {
            public String command { get; set; }
            public String desc { get; set; }
            public TouchPoint point { get; set; }
        }
      
        public ArduinoCom(ConnectionEstablished ce, PointReceived pr, PressureReceived psr, CalibrationComplete cc)
        {
            connEstablished = ce;
            ptRecd = pr;
            pressureRecd = psr;
            calComplete = cc;

            arduinoPort = new SerialPort();
            arduinoPort.PortName = GlobalPreferences.comPort;
            arduinoPort.BaudRate = BAUDRATE;
            arduinoPort.DataBits = 8;
            arduinoPort.StopBits = StopBits.One;
            arduinoPort.Parity = Parity.None;
            arduinoPort.DtrEnable = true;        
        }

        // Event handler for the data received event for the serial port.  ReadByte and Read Char aren't working
        /*private void dataReceivedHandler(object sender,
                        SerialDataReceivedEventArgs e)
        {

        }*/

        public void connect()
        {
            Thread connThread = new Thread(connectThread);
            connThread.Start();
        }

        private void connectThread()
        {
            Thread.Sleep(1000);  
            arduinoPort.Open();

            Stopwatch timer = new Stopwatch();

            // give the serial port 10 seconds to open
            while (!arduinoPort.IsOpen && timer.ElapsedMilliseconds <= 10000);

            if (arduinoPort.IsOpen)
            {
                arduinoPort.DiscardInBuffer();
                arduinoPort.DiscardOutBuffer();
            }
            connEstablished(arduinoPort.IsOpen);
        }

        public void disconnect()
        {
            mRunning = false;

            if (arduinoPort.IsOpen)
                arduinoPort.Close();
        }

        public bool startCalibrationThread()
        {
            if (!arduinoPort.IsOpen)
                return false;

            mRunning = true;
            calibrateThread = new Thread(this.calibrate);
            calibrateThread.Start();
            return true;

        }

        private void calibrate()
        {
            //TODO, get points here
            TouchPoint[] screenPts = getTouchPoints();
            if (screenPts == null)
            {              
                return;
            }

            TouchPoint resistance = getResistance();
            if (resistance == null)
            {               
                return;
            }

            if (!calculateCoefficients(screenPts[0], screenPts[1], screenPts[2], resistance))
            {              
                return;
            }

            if (!setRotation())
            {
                return;
            }

            this.writeString("<CAL_SUCCESS>");
            calComplete(true);

        }

        private ArduinoMessage readMessage()
        {
            ArduinoMessage message;
            StringBuilder buffer = new StringBuilder();
            int ch;
            // spin until we receive the opening packet
            while (mRunning && (ch = arduinoPort.ReadChar()) != '<')
            {
                if (ch != 0)
                {
                    Debug.WriteLine((char)ch);
                }

                Thread.Sleep(5);
            }

            while (mRunning && (ch = arduinoPort.ReadChar()) != '>')
            {
                buffer.Append((char)ch);
            }

            String msg = buffer.ToString();
            String[] tokens = msg.Split(':');

            if (tokens.Length == 2)
            {
                // command received or log message
                message = new ArduinoMessage();

                message.command = tokens[0];
                message.desc = tokens[1];

            }
            else if (tokens.Length == 4)
            {
                // Point received
                message = new ArduinoMessage();
                message.command = tokens[0];
                message.point = new TouchPoint(int.Parse(tokens[1]), int.Parse(tokens[2]), int.Parse(tokens[3]));

            }
            else {
                Debug.WriteLine("ArduinoCom: Issue parsing string, invalid data recd: " + msg);
                message = null;
            }

            return message;
        }

        private void writeString(String data)
        {
            byte[] buffer = Encoding.ASCII.GetBytes(data);
            arduinoPort.Write(buffer, 0, buffer.Length);
        }

        private TouchPoint[] getTouchPoints()
        {
            TouchPoint[] touchPoints = new TouchPoint[3];
            ArduinoMessage screenPt;

            // Get the three calibration touch points
            for (int i = 0; i < 3; i++)
            {           
                // Tell the Arudino to recieve a single point
                this.writeString("<CAL_POINT>");
                /*ArduinoMessage receipt;
                receipt = readMessage();
                if (!receipt.desc.Equals("GET_POINT_OK"))
                {
                    Debug.WriteLine(receipt.command + " " + receipt.desc);
                    this.writeString("<ERROR>");
                    ptRecd(false, 0);
                    return null;
                }
                else
                {
                    Debug.WriteLine(receipt.desc);
                }*/

                screenPt = readMessage();
                if (screenPt == null)
                {
                    // Error parsing bytes
                    Debug.WriteLine("Error Parsing Calibration data from arduino");
                    ptRecd(false, i);
                    return null;
                }
                touchPoints[i] = new TouchPoint(screenPt.point.x, screenPt.point.y, screenPt.point.z);

                Debug.WriteLine("ArduinoCom: Point " + i + " X value: " + touchPoints[i].x);
                Debug.WriteLine("ArduinoCom: Point " + i + " Y value: " + touchPoints[i].y);

                // Callback to the activity telling it we received a point
                ptRecd(true, i + 1);

                // Sleep for one second so the UI has time to animate to the next point
                Thread.Sleep(1000);
            }
            return touchPoints;
        }

        private TouchPoint getResistance()
        {
            ArduinoMessage pressurePt = new ArduinoMessage();
            bool calComplete = false;
            int resMin = 65535;
            int resMax = 0;

            Stopwatch timer = new Stopwatch();

            this.writeString("<CAL_PRESSURE>");

            
            /*ArduinoMessage receipt;
            receipt = readMessage();
            if (!receipt.desc.Equals("GET_PRESSURE_OK"))
            {
                Debug.WriteLine(receipt.command + " " + receipt.desc);
                this.writeString("<ERROR>");
                pressureRecd(false);
                return null;
            }
            else
            {
                Debug.WriteLine(receipt.desc);
            }*/

            timer.Start();
            while (!calComplete && timer.ElapsedMilliseconds <= 10000)
            {
                pressurePt = readMessage();
                if (pressurePt == null)
                {
                    // Error parsing bytes
                    Debug.WriteLine("ArduinoCom: Error Parsing Pressure Calibration data from arduino");
                    break;
                }
                // We'll receive a stop command from the arduino after the user has
                // lifted their finger
                if (pressurePt.command.Equals("STOP"))
                {
                    calComplete = true;
                    break;
                }
                
                if (pressurePt.point.z < resMin)
                {
                    resMin = pressurePt.point.z;
                }
                if (pressurePt.point.z > resMax)
                {
                    resMax = pressurePt.point.z;
                }
            }

            if (calComplete)
            {
                //pressure calibration was completed successfully
                pressureRecd(true);
                return new TouchPoint(resMin, resMax, 0);
            }
            else
            {
                pressureRecd(false);
                return null;
            }

        }

        private bool calculateCoefficients(TouchPoint T1, TouchPoint T2, TouchPoint T3, TouchPoint resistance)
        {
            double A;
            double B;
            double C;
            double D;
            double E;
            double F;

            TouchPoint D1;     // Right Center device coordinate
            TouchPoint D2;     // Bottom Center device coordinate
            TouchPoint D3;     // Top Left device coordinate

            if (GlobalPreferences.devType == DeviceType.UINPUT)
            {
                // uInput device, calculate points from preferences
                int xMax = GlobalPreferences.deviceWidth;
                int yMax = GlobalPreferences.deviceHeight; 

                int xOffset = (int)Math.Round(0.1f * xMax);
                int yOffset = (int)Math.Round(0.1f * yMax);

                // Points are zero indexed, so we always subtract 1 pixel
                D1 = new TouchPoint(((xMax - xOffset) - 1),
                        ((yMax / 2) - 1));
                D2 = new TouchPoint(((xMax / 2) - 1),
                        ((yMax - yOffset) - 1));
                D3 = new TouchPoint((xOffset - 1), (yOffset - 1));

            }
            else {
                // HID device, each axis goes from 0 - 10000
                D1 = new TouchPoint(9000, 5000);
                D2 = new TouchPoint(5000, 9000);
                D3 = new TouchPoint(1000, 1000);
            }

            A = (D1.x * (T2.y - T3.y)) + (D2.x * (T3.y - T1.y))
                + (D3.x * (T1.y - T2.y));
            A = A / ((T1.x * (T2.y - T3.y)) + (T2.x * (T3.y - T1.y))
                    + (T3.x * (T1.y - T2.y)));

            B = (A * (T3.x - T2.x)) + D2.x - D3.x;
            B = B / (T2.y - T3.y);

            C = D3.x - (A * T3.x) - (B * T3.y);

            D = ((D1.y * (T2.y - T3.y)) + (D2.y * (T3.y - T1.y))
                    + (D3.y * (T1.y - T2.y)));
            D = D / ((T1.x * (T2.y - T3.y)) + (T2.x * (T3.y - T1.y))
                    + (T3.x * (T1.y - T2.y)));

            E = ((D * (T3.x - T2.x)) + D2.y - D3.y);
            E = E / (T2.y - T3.y);

            F = D3.y - (D * T3.x) - (E * T3.y);

            Debug.WriteLine("A coefficient: " + A);
            Debug.WriteLine("B coefficient: " + B);
            Debug.WriteLine("C coefficient: " + C);
            Debug.WriteLine("D coefficient: " + D);
            Debug.WriteLine("E coefficient: " + E);
            Debug.WriteLine("F coefficient: " + F);

            int tmp;
            tmp = (int)Math.Round(A * 10000);
            if (!sendCalibrationVariable("<$A:" +  tmp.ToString() + ">")) return false;

            tmp = (int)Math.Round(B * 10000);
            if (!sendCalibrationVariable("<$B:" + tmp.ToString() + ">")) return false;

            tmp = (int)Math.Round(C * 10000);
            if (!sendCalibrationVariable("<$C:" + tmp.ToString() + ">")) return false;

            tmp = (int)Math.Round(D * 10000);
            if (!sendCalibrationVariable("<$D:" + tmp.ToString() + ">")) return false;

            tmp = (int)Math.Round(E * 10000);
            if (!sendCalibrationVariable("<$E:" + tmp.ToString() + ">")) return false;

            tmp = (int)Math.Round(F * 10000);
            if (!sendCalibrationVariable("<$F:" + tmp.ToString() + ">")) return false;

            if (!sendCalibrationVariable("<$M:" + resistance.x.ToString() + ">")) return false;

            return true;
        }
    

        private bool sendCalibrationVariable(String varData)
        {
            ArduinoMessage receipt;
            this.writeString(varData);
            receipt = readMessage();
            if (!receipt.desc.Equals("OK"))
            {
                Debug.WriteLine(receipt.command + " " + receipt.desc);
                this.writeString("<ERROR>");
                return false;
            }
            else {
                ArduinoMessage value = readMessage();
                Debug.WriteLine(value.desc);
                return true;
            }
        }

        private bool setRotation()
        {
            int rot = (int)GlobalPreferences.rotation;
            String data = "<SET_ROTATION:" + rot.ToString() + ">";
            this.writeString(data);

            ArduinoMessage receipt = readMessage();
            if (receipt == null)
            {               
                return false;
            }
            if (receipt.desc.Equals("OK"))
            {
                ArduinoMessage value = readMessage();
                Debug.WriteLine(value.desc);
                return true;
            }
            else {
                Debug.WriteLine(receipt.command + " " + receipt.desc);
                this.writeString("<ERROR>");
                return true;
            }
        }

        public void setOnlyRotation()
        {
            mRunning = true;
            Thread setRotation = new Thread(setOnlyRotationThread);
            setRotation.Start();
        }

        private void setOnlyRotationThread()
        {
            if (setRotation())
            {
                this.writeString("<CAL_SUCCESS>");
                calComplete(true);
            }
            else
            {
                calComplete(false);
            }

        }
    }
}


