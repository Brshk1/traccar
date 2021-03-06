/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function () {
    'use strict';

    Ext.define('Traccar.LoginManager', {
        singleton: true,

        server: function (options) {
            Ext.Ajax.request({
                scope: this,
                url: '/api/server/get',
                callback: this.onServerReturn,
                original: options
            });
        },

        onServerReturn: function (options, success, response) {
            var result;
            options = options.original;
            if (Traccar.ErrorManager.check(success, response)) {
                result = Ext.decode(response.responseText);
                if (result.success) {
                    Traccar.app.setServer(result.data);
                }
                Ext.callback(options.callback, options.scope, [result.success]);
            }
        },

        session: function (options) {
            Ext.Ajax.request({
                scope: this,
                url: '/api/session',
                callback: this.onSessionReturn,
                original: options
            });
        },

        onSessionReturn: function (options, success, response) {
            var result;
            options = options.original;
            if (Traccar.ErrorManager.check(success, response)) {
                result = Ext.decode(response.responseText);
                if (result.success) {
                    Traccar.app.setUser(result.data);
                }
                Ext.callback(options.callback, options.scope, [result.success]);
            }
        }
    });

})();
