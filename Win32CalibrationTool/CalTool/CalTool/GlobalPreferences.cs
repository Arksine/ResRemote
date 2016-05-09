using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CalTool
{

    enum DeviceType { UINPUT, HID }
    enum Rotation { ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270}

    class GlobalPreferences
    {
       
        public static String comPort { get; set; }
        public static DeviceType devType { get; set; }
        public static Rotation rotation { get; set; }
        public static int deviceWidth { get; set; }
        public static int deviceHeight { get; set; }

        // Create private empty constructor so static class cannot be instantiated
        private GlobalPreferences(){}
    }
}
