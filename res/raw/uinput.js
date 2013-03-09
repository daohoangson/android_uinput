(function($) {
	var $document = $(document);
	var $screen;
	var widthScale, heightScale;

	var getLinuxKeyCombo = function(normalized) {
		// ideas from http://www.math.bme.hu/~morap/RemoteInput/
		// q,w,e,r,t,y,u,i,o,p,a,s,d,f,g,h,j,k,l,z,x,c,v,b,n,m
		var qwerty = [ 30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49,
				24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44 ];

		var specialChars = {};
		specialChars[8] = 14; // <backspace>
		specialChars[9] = 15; // <tab>
		specialChars[13] = 28; // <enter>
		specialChars[27] = 158; // <esc> -> <back>
		specialChars[32] = 57; // <space>
		specialChars[37] = 105; // <left>
		specialChars[38] = 103; // <up>
		specialChars[39] = 106; // <right>
		specialChars[40] = 108; // <down>
		specialChars[44] = 51; // ,
		specialChars[62] = 52; // .
		specialChars[47] = 53; // /
		specialChars[59] = 39; // ;
		specialChars[61] = 13; // =
		specialChars[91] = 26; // [
		specialChars[92] = 43; // \
		specialChars[93] = 27; // ]
		specialChars[95] = 12; // - ***
		specialChars[126] = 41; // ` ***
		specialChars[222] = 40; // ' (looks like a special case from keycode.js)

		var jsKeyCode = normalized.code;
		var linuxKeyCode = 0;

		if (65 <= jsKeyCode && jsKeyCode <= 90) {
			// 65 = A
			// 90 = Z
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
			linuxKeyCode = specialChars[jsKeyCode];
		}

		return [ linuxKeyCode, normalized.shift, normalized.alt,
				normalized.ctrl ];
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
		var btnLeftDown = false;

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
			if (btnLeftDown == false) {
				// do not send pointless move reqests
				return;
			}

			var deviceXY = getDeviceXY(e);
			var data = {
				x : deviceXY[0],
				y : deviceXY[1]
			};

			console.log('mousemove', data);
			$.ajax('/pointer', {
				type : 'POST',
				data : data
			});

			lastMouseMoveSent = timestamp;
			btnTouchDown = true;

			e.preventDefault();
		});

		$screen.mousedown(function(e) {
			var deviceXY = getDeviceXY(e);
			var data = {
				x : deviceXY[0],
				y : deviceXY[1],
				btn_left : (btnLeftDown ? '' : 'down')
			};
			data.btn_touch = data.btn_left;

			console.log('mousedown', data);
			$.ajax('/pointer', {
				type : 'POST',
				data : data
			});

			btnLeftDown = true;

			e.preventDefault();
		});

		var mouseUpOrOut = function(e) {
			var deviceXY = getDeviceXY(e);
			var data = {
				x : deviceXY[0],
				y : deviceXY[1],
				btn_left : (btnLeftDown ? 'up' : '')
			};
			data.btn_touch = data.btn_left;

			console.log('mouseup', data);
			$.ajax('/pointer', {
				type : 'POST',
				data : data
			});

			btnLeftDown = false;

			e.preventDefault();
		};

		$screen.mouseup(mouseUpOrOut);
		$screen.mouseout(mouseUpOrOut);

		$document.keydown(function(e) {
			e.preventDefault();

			var normalized = KeyCode.translate_event(e);
			var combo = getLinuxKeyCombo(normalized);
			var key = combo[0];
			var shift = combo[1];

			if (key == 0) {
				// do not send keypress for unknown key
				if (e.keyCode != 16 && e.keyCode != 17 && e.keyCode != 18) {
					// skip printing error for shift, ctrl and alt keys
					console.error('unknown key', e.keyCode, normalized, KeyCode
							.hot_key(normalized));
				}
				return;
			}

			console.log('keydown', key);
			$.ajax('/key/press', {
				type : 'POST',
				data : {
					key : key,
					shift : (shift ? 1 : 0)
				}
			});
		});
	}

	var refreshScreen = function() {
		var origSrc = $screen.attr('origSrc');
		if (typeof origSrc == 'undefined') {
			origSrc = $screen.attr('src');
			$screen.attr('origSrc', origSrc);
		}

		var newSrc = origSrc + '?timestamp=' + new Date().getTime();
		console.log(newSrc);
		var $img = $('<img />').css('position', 'absolute').css('width', '0')
				.css('height', '0').insertAfter($screen);
		$img.load(function() {
			$screen.attr('src', $img.attr('src'));
			setTimeout(refreshScreen, 250);
		});
		$img.attr('src', newSrc);
	};

	$document.ready(function() {
		$screen = $('#screen');

		setupScreen();
		setupEvents();
		refreshScreen();
	});
})(jQuery);