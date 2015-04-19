'use strict';

var helpApp = angular.module('helpApp', ['ngRoute']);


helpApp.config(['$routeProvider', '$locationProvider', '$provide', function ($routeProvider, $locationProvider, $provide) {

    $routeProvider.
            when('/:page', {templateUrl: function(params){ return params.page;}, controller: 'viewController'}).
            when('/applications/:page', {templateUrl: function(params){ return 'applications/'+ params.page;}, controller: 'viewController'}).
            when('/language/:page', {templateUrl: function(params){ return 'language/'+ params.page;}, controller: 'viewController'}).
            when('/syntax/:page', {templateUrl: function(params){ return 'syntax/'+ params.page;}, controller: 'viewController'}).
            otherwise({redirectTo: '/title.html'});
    }]);


helpApp.controller('viewController',['$scope', '$route', '$routeParams', '$location', '$log', '$window', '$http', '$filter',
    function($scope, $route, $routeParams, $location, $log, $window, $http, $filter) {

}]);

helpApp.controller('pagesController',['$scope', '$log', '$http', '$filter', function($scope, $log, $http, $filter) {

        $scope.mainPages = '';
        $scope.syntaxPages = '';
        $scope.languagePages = '';
        $scope.applicationsPages = '';

        function handleError(data, status) {
            $log.error('Could not execute request because of ' + status + '! Response is ' + $filter('json')(data) + '!');
        }

        function handleSuccess(data) {
            $log.debug('Request returned ' + $filter('json')(data) + '.');
            $scope.mainPages = data.mainPages;
            $scope.syntaxPages = data.syntax;
            $scope.languagePages = data.language;
            $scope.applicationsPages = data.applications;
        }

        $scope.loadPages = function(){
            $http.get("package.json").success(handleSuccess).error(handleError);
        };

        $scope.loadPages();

}]);



