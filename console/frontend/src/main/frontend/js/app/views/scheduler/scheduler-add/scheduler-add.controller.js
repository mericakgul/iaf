import { appModule } from "../../../app.module";

appModule.controller('AddScheduleCtrl', ['$scope', 'Api', '$rootScope', function ($scope, Api, $rootScope) {
	$scope.state = [];
	$scope.addLocalAlert = function (type, message) {
		$scope.state.push({ type: type, message: message });
	};

	$rootScope.$watch('configurations', function () { $scope.configurations = $rootScope.configurations; });
	$rootScope.$watch('adapters', function () { $scope.adapters = $rootScope.adapters; });

	$scope.selectedConfiguration = "";
	$scope.form = {
		name: "",
		group: "",
		adapter: "",
		listener: "",
		cron: "",
		interval: "",
		message: "",
		description: "",
		locker: false,
		lockkey: "",
	};

	$scope.submit = function () {
		var fd = new FormData();
		$scope.state = [];

		fd.append("name", $scope.form.name);
		fd.append("group", $scope.form.group);
		fd.append("configuration", $scope.selectedConfiguration);
		fd.append("adapter", $scope.form.adapter);
		fd.append("listener", $scope.form.listener);
		fd.append("cron", $scope.form.cron);
		fd.append("interval", $scope.form.interval);
		fd.append("message", $scope.form.message);
		fd.append("description", $scope.form.description);
		fd.append("locker", $scope.form.locker);
		fd.append("lockkey", $scope.form.lockkey);

		Api.Post("schedules", fd, function (data) {
			$scope.addLocalAlert("success", "Successfully added schedule!");
			$scope.selectedConfiguration = "";
			$scope.form = {
				name: "",
				group: "",
				adapter: "",
				listener: "",
				cron: "",
				interval: "",
				message: "",
				description: "",
				locker: false,
				lockkey: "",
			};
		}, function (errorData, status, errorMsg) {
			var error = (errorData) ? errorData.error : errorMsg;
			$scope.addLocalAlert("warning", error);
		}, false);
	};
}]);