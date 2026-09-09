import {defineConfig} from '@playwright/test';
export default defineConfig({
    testDir:'./src/test/browser',fullyParallel:false,workers:1,timeout:60000,
    use:{baseURL:'http://127.0.0.1:8085',browserName:'chromium',viewport:{width:1280,height:900},trace:'retain-on-failure'},
    webServer:{command:'java -Djava.awt.headless=true -jar target/pdf-accessibility-assistant-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8085',url:'http://127.0.0.1:8085',timeout:60000,reuseExistingServer:false},
});
