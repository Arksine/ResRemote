   M S B A M L   `   `   `  ����B  >CalTool, Version=1.0.0.0, Culture=neutral, PublicKeyToken=null!clr-namespace:CalToolCalTool  5   	       CalTool.MainWindow   5   	   W SPresentationCore, Version=4.0.0.0, Culture=neutral, PublicKeyToken=31bf3856ad364e35R NWindowsBase, Version=4.0.0.0, Culture=neutral, PublicKeyToken=31bf3856ad364e35\ XPresentationFramework, Version=4.0.0.0, Culture=neutral, PublicKeyToken=31bf3856ad364e35D 9http://schemas.microsoft.com/winfx/2006/xaml/presentation    5   	   R NSystem.Xaml, Version=4.0.0.0, Culture=neutral, PublicKeyToken=b77a5c561934e089:x,http://schemas.microsoft.com/winfx/2006/xaml     5   	   @d2http://schemas.microsoft.com/expression/blend/2008     5   	   Jmc;http://schemas.openxmlformats.org/markup-compatibility/2006     5   	   'localclr-namespace:CalTool     5   	   -   5          �Name$  
mainWindow��5   	    � Title$ Calibration Tool��5   	   $	��345��6"   $	��320��6/    � Icon$ CalToolIcon.ico��6;    � 	Resources 5
         b2v��%   &	        �� 5   
   6'   5      .��5      �  � RowDefinitions 5   
   �� 5      $
��Auto �6   �� 5      $
��Auto �6   �� 5      $
��Auto �6   �� 5      $
��Auto �6   .+�5   
   �� $��Select Device Type��6   .��5      �� .+�5      	� -   5         	�Name$ rbtHid��5      $	��HID��6.   $��5q�6<    d� 	IsChecked$
 TrueY�6G    	� 	GroupName$ 
deviceType��6X   �� 5       �� Orientation$ 
Horizontal=�6   .+�5      	� -   5        $ 	rbtUinput��5   "   $��uInput��65   $��5q�6F   $ 
deviceType��6Q   �� 5      $��Width:��5      $��20,0,0,0q�6-   	 � ToolTip$�	 �The width of the target device in pixels.                              Make sure the width reflects the orientation you want to use.��6?   y�5      �� *	IsChecked+
 �� ElementName$
 	rbtUinput�� �� 	Converter# [  5   ?   �� 5       -   5         ��Name$ tbxWidth��5!      $��50��60   $�	 �The width of the target device in Pixels.                               Make sure the width reflects the orientation you want to use.��6;   $��18��5"   ]   y�5       �� *	IsChecked+$
 	rbtUinput��# [  5"   ]   �� 5#      $��Height:��5$      $�	 �The height of the target device in pixels.                             Make sure the height reflects the orientation you want to use.��6.   y�5#      �� *	IsChecked+$
 	rbtUinput��# [  5$   .   �� 5&      -   5        $ 	tbxHeight��5'      $��50��61   $�	 �The height of the target device in Pixels.                               Make sure the height reflects the orientation you want to use.��6<   $��18��5(   ^   y�5&      �� *	IsChecked+$
 	rbtUinput��# [  5(   ^   5)      5*      5+      �� 5-   
   $��1��6   $��Select Device Rotation��6    $�	 �Select the standard rotation for the device you will control.  For destop applicatons, this is typically Rotation 0.  For mobile applications, this is typicaly Rotation 90 or Rotation 270 (landscape mode)��6@   .��5.      �� .+�5/      	� -   5        $ rbtRot0��5/      $��
Rotation 0��6/   $��5q�6D   $ rotation��6O   $
 TrueY�6d   	� 50      -   5        $ rbtRot90��50      $��Rotation 90��60   $��5q�6F   $ rotation��6Q   	� 51      -   5        $ 	rbtRot180��51      $��Rotation 180��61   $��5q�6H   $ rotation��6S   	� 52      -	   5        $ 	rbtRot270��52      $��Rotation 270��61   $��5q�6H   $ rotation��6S   53      55      �� 57   
   $��2��6   $��Select COM Port��6    .��58      �� .+�59      ]� .+�5:      �� -
   5         ��Name$ 
btnRefresh��5:      $��Refresh��61   $��5q�6C   6N   �� 5;      -   5         ��Name$ cbxSelectDevice��5;      $	 Select Device��68   $��5q�6P   5<      �� 5=      -   5         ��Name$ ckbComDetails��5=      $(��"Show detailed COM port description��62   $��5q�6_   $�	 �Warning:  When enabled, the Windows Management Instrumentation is queried to populate the box below with com port details.  It is a slow operation, and can take up to 10 seconds.��6j   5>      5?      �� 5@   
   $��3��6   $ 
Horizontal=�6"   .+�5A      �� -   5        $ btnStartCalibration��5A      $��Start Calibration��62   $��5q�6N   $
��Left=�6{   $	��140��6�   �� 5B      -   5        $ btnChangeOrientaton��5B      $��Change Orientation Only��62   $��10,5,5,5q�6T   $
��Left=�6f   $	��140��6�   $h	 bDoes not calibrate the device, only changes the orientation in which the device expects to output.��6�   5C      5D      5E      