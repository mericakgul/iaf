import { appModule } from "./app.module";

appModule.directive('pageTitle', ['$rootScope', '$timeout', '$state', '$transitions', 'Debug', 'appService', function ($rootScope: angular.IRootScopeService, $timeout: angular.ITimeoutService, $state, $transitions, Debug, appService) {
	return {
		link: function (scope, element) {
			var listener = function () {
				var toState = $state.current;
				Debug.info("state change", toState);

				var title = 'Loading...'; // Default title
				if (toState.data && toState.data.pageTitle && appService.instanceName) title = appService.dtapStage + '-' + appService.instanceName + ' | ' + toState.data.pageTitle;
				else if (appService.startupError) title = "ERROR";
				$timeout(function () {
					element.text(title);
				});
      };
			$transitions.onSuccess({}, () => listener()); //Fired on every state change
      appService.instanceName$.subscribe(() => listener()); //Fired once, once the instance name is known.
      appService.startupError$.subscribe(() => listener()); //Fired once, once the startup error is known if there is one.
		}
	};
}]);