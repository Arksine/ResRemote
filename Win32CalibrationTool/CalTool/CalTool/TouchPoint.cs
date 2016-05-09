using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CalTool
{
    class TouchPoint
    {

        public int x { get; }
        public int y { get; }
        public int z { get; }

        public TouchPoint(int x, int y, int z = 0)
        {

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public override bool Equals(object obj)
        {
            if (!(obj is TouchPoint))
                return false;

            TouchPoint coord = (TouchPoint)obj;

            return (this.x == coord.x && this.y == coord.y && this.z == coord.z);
        }

        public override int GetHashCode()
        {
            int hash = 4;
            hash = (hash * 7) + this.x.GetHashCode();
            hash = (hash * 7) + this.y.GetHashCode();
            hash = (hash * 7) + this.z.GetHashCode();
            return hash;
        
        }
    }
}
