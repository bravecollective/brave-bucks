import { Component, OnInit } from '@angular/core';
import {ConfigService, Principal} from "../../shared";
import {Http} from "@angular/http";
import {SolarSystem} from "../../entities/solar-system/solar-system.model";

@Component({
  selector: 'jhi-info-block',
  templateUrl: './info-block.component.html',
  styles: []
})
export class InfoBlockComponent implements OnInit {

    regions = ['Catch', 'Impass', 'Feythabolis', 'Querious'];
    systems = {};

    walletUrl: string;
    characterNames: string[];

    constructor(private configService: ConfigService, private principal: Principal, private http: Http) {
    }

    ngOnInit() {
        this.principal.identity().then((account) => {
            this.configService.getWalletUrl().subscribe((data) => this.walletUrl = data + "-" + account.id);
        });

        for (let region of this.regions) {
            this.http.get('/api/solar-systems/region/'+region.toUpperCase()).subscribe((data) => {
                this.systems[region] = data.json();
            });
        }
    }

    revokeCharacter(characterName: string) {
        this.http.delete('/api/characters/' + characterName).subscribe((data) => {
            this.http.get('/api/characters').subscribe((charNames) => {
                this.characterNames = charNames.json();
            });
        });
    }

    getDotlanLink(region: string, isPvp: boolean, isRatting: boolean) {
        const systemNames = [];
        if (this.systems.hasOwnProperty(region)) {
            this.systems[region].forEach((s) => {
                if (isPvp && s.trackPvp || isRatting && s.trackRatting) {
                    systemNames.push(s.systemName);
                }
            });
        }
        let url = "http://evemaps.dotlan.net/map/" + region + '/' + systemNames.join();
        if (isRatting) {
            url += "#adm";
        }
        return url;
    }

}
