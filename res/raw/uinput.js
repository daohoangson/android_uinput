(function($) {
	var $document = $(document);
	var $screen;
	var widthScale, heightScale;

	var getLinuxKeyCombo = function(e) {
		// ideas from http://www.math.bme.hu/~morap/RemoteInput/
		// q,w,e,r,t,y,u,i,o,p,a,s,d,f,g,h,j,k,l,z,x,c,v,b,n,m
		var qwerty = [ 30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49,
				24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44 ];
		
		var specialChars = {};
		specialChars[32] = [57, false]; // <space>
		specialChars[33] = [2, true]; // !
		specialChars[34] = [40, true]; // "
		specialChars[35] = [4, true]; // #
		specialChars[36] = [5, true]; // $
		specialChars[37] = [6, true]; // %
		specialChars[38] = [8, true]; // &
		specialChars[39] = [40, false]; // '
		specialChars[40] = [10, true]; // (
		specialChars[41] = [11, true]; // )
		specialChars[42] = [9, true]; // *
		specialChars[43] = [13, true]; // +
		specialChars[44] = [51, false]; // ,
		specialChars[45] = [12, false]; // -
		specialChars[46] = [52, false]; // .
		specialChars[47] = [53, false]; // /
		specialChars[58] = [39, true]; // :
		specialChars[59] = [39, false]; // ;
		specialChars[60] = [51, true]; // <
		specialChars[61] = [13, false]; // =
		specialChars[62] = [52, true]; // >
		specialChars[63] = [53, true]; // ?
		specialChars[64] = [3, true]; // @
		specialChars[91] = [26, false]; // [
		specialChars[92] = [43, false]; // \
		specialChars[93] = [27, false]; // ]
		specialChars[94] = [7, true]; // ^
		specialChars[95] = [12, true]; // _
		specialChars[96] = [41, false]; // `
		specialChars[123] = [26, true]; // {
		specialChars[124] = [43, true]; // |
		specialChars[125] = [27, true]; // }
		specialChars[126] = [41, true]; // ~
		specialChars[127] = [14, false]; // <del>

		var jsKeyCode = e.keyCode;
		var linuxKeyCode = 0;
		var shift = false

		if (97 <= jsKeyCode && jsKeyCode <= 122) {
			// 97 = a
			// 122 = z
			linuxKeyCode = qwerty[jsKeyCode - 97];
		}
		if (65 <= jsKeyCode && jsKeyCode <= 90) {
			// 65 = A
			// 90 = Z
			shift = true;
			linuxKeyCode = qwerty[jsKeyCode - 65];
		}

		if (49 <= jsKeyCode && jsKeyCode <= 57) {
			// 49 = 1
			// 57 = 9
			linuxKeyCode = jsKeyCode - 49 + 2;
		}
		if (jsKeyCode == 48) {
			// 48 = 0
			linuxKeyCode = 11;
		}
		
		if (typeof specialChars[jsKeyCode] !== 'undefined') {
			linuxKeyCode = specialChars[jsKeyCode][0];
			shift = specialChars[jsKeyCode][1];
		}

		return [ linuxKeyCode, shift ];
	}

	var setupScreen = function() {
		var ratio = deviceInfo.screenWidth / deviceInfo.screenHeight;
		var maxWidth = $screen.parent().width();
		var maxHeight = $screen.parent().height();

		var width = maxWidth;
		var height = width / ratio;
		if (width > maxWidth || height > maxHeight) {
			height = maxHeight;
			width = height * ratio;
		}
		width = Math.floor(width);
		height = Math.floor(height);
		widthScale = deviceInfo.screenWidth / width;
		heightScale = deviceInfo.screenHeight / height;

		$screen.css('width', '' + width + 'px').css('height',
				'' + height + 'px').css('background', 'black');
	};

	var setupEvents = function() {
		var lastMouseMoveSent = 0;

		var getDeviceXY = function(e) {
			return [ Math.floor(e.clientX * widthScale),
					Math.floor(e.clientY * heightScale) ];
		}

		$screen.mousemove(function(e) {
			var timestamp = new Date().getTime();
			if (timestamp - lastMouseMoveSent < 100) {
				// do not send more than once per 100ms
				return;
			}

			var deviceXY = getDeviceXY(e);

			console.log('mousemove', deviceXY[0], deviceXY[1]);
			$.ajax('/pointer', {
				type : 'POST',
				data : {
					x : deviceXY[0],
					y : deviceXY[1],
					btn_touch : 'down'
				}
			});

			lastMouseMoveSent = timestamp;
		});

		$screen.mousedown(function(e) {
			var deviceXY = getDeviceXY(e);

			console.log('mousedown', deviceXY[0], deviceXY[1]);
			$.ajax('/pointer', {
				type : 'POST',
				data : {
					x : deviceXY[0],
					y : deviceXY[1],
					btn_touch : 'down',
					btn_left : 'down'
				}
			});
		})

		$screen.mouseup(function(e) {
			var deviceXY = getDeviceXY(e);

			console.log('mouseup', deviceXY[0], deviceXY[1]);
			$.ajax('/pointer', {
				type : 'POST',
				data : {
					x : deviceXY[0],
					y : deviceXY[1],
					btn_touch : 'up',
					btn_left : 'up'
				}
			});
		})

		$document.keypress(function(e) {
			// TODO: convert e.keyCode to Linux key code
			var combo = getLinuxKeyCombo(e);
			var key = combo[0];
			var shift = combo[1];
			
			if (key == 0) {
				// do not send keypress for unknown key
				console.error('unknown key', e);
				return;
			}

			console.log('keypress', key);
			$.ajax('/key/press', {
				type : 'POST',
				data : {
					key : key,
					shift : (shift ? 1 : 0)
				}
			});
		})
	}

	$document.ready(function() {
		$screen = $('#screen');

		setupScreen();
		setupEvents();
	});
})(jQuery);