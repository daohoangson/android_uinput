(function($) {
	var $document = $(document);
	var $screen;
	var widthScale, heightScale;
	
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
		
		$screen.css('width', '' + width + 'px')
			.css('height', '' + height + 'px')
			.css('background', 'black');
	};
	
	var setupEvents = function() {
		var lastMouseMoveSent = 0;
		
		var getDeviceXY = function(e) {
			return [Math.floor(e.clientX * widthScale), Math.floor(e.clientY * heightScale)];
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
				type: 'POST',
				data: {
					x: deviceXY[0],
					y: deviceXY[1],
					btn_touch: 'down'
				}
			});
			
			lastMouseMoveSent = timestamp;
		});
		
		$screen.mousedown(function(e) {
			var deviceXY = getDeviceXY(e);
			
			console.log('mousedown', deviceXY[0], deviceXY[1]);
			$.ajax('/pointer', {
				type: 'POST',
				data: {
					x: deviceXY[0],
					y: deviceXY[1],
					btn_touch: 'down',
					btn_left: 'down'
				}
			});
		})
		
		$screen.mouseup(function(e) {
			var deviceXY = getDeviceXY(e);
			
			console.log('mouseup', deviceXY[0], deviceXY[1]);
			$.ajax('/pointer', {
				type: 'POST',
				data: {
					x: deviceXY[0],
					y: deviceXY[1],
					btn_touch: 'up',
					btn_left: 'up'
				}
			});
		})
		
		$document.keypress(function(e) {
			// TODO: convert e.keyCode to Linux key code
			var key = e.keyCode;
			
			console.log('keypress', key);
			$.ajax('/key/press', {
				type: 'POST',
				data: {
					key: key
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